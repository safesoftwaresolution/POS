package com.sciencebot.pos.voicesales.internal.resolution;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Candidate;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Reference;
import com.sciencebot.pos.voicesales.internal.ports.SaleDraftResolver;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntent;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind.*;
import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Status.*;

/** Read-only catalog suggestions; confirmation always belongs to the existing sales API. */
@Component
public class VoiceSaleResolver implements SaleDraftResolver {
    private static final PageRequest CANDIDATE_PAGE = PageRequest.of(0, 20);
    private final ProductFacade products;
    private final ServiceFacade services;
    private final CustomerFacade customers;
    private final SettingsFacade settings;

    public VoiceSaleResolver(ProductFacade products, ServiceFacade services, CustomerFacade customers,
                             SettingsFacade settings) {
        this.products = products;
        this.services = services;
        this.customers = customers;
        this.settings = settings;
    }

    @Override
    public VoiceSalePreviewDto resolve(SaleIntent intent) {
        if (TenantContext.getStoreId() == null) {
            throw new IllegalStateException("An authenticated store is required for voice sale resolution");
        }
        if (intent == null) {
            throw new IllegalArgumentException("A sale intent is required");
        }
        List<String> pending = new ArrayList<>();
        List<VoiceSalePreviewDto.Line> lines = new ArrayList<>();
        List<SaleIntent.Item> items = intent.items() == null ? List.of() : intent.items();
        if (items.isEmpty()) {
            pending.add("items");
        }
        for (int index = 0; index < items.size(); index++) {
            SaleIntent.Item item = items.get(index);
            Reference reference = resolveItem(item == null ? null : item.mention());
            String path = "items[" + index + "]";
            reviewUnlessResolved(reference, path + ".item", pending);
            Integer quantity = item == null ? null : item.quantity();
            if (quantity == null || quantity <= 0) {
                pending.add(path + ".quantity");
            }
            lines.add(new VoiceSalePreviewDto.Line(reference, quantity));
        }
        Reference customer = resolveCustomer(intent.customer());
        Reference payment = resolvePayment(intent.paymentMethod());
        reviewUnlessResolved(customer, "customer", pending);
        reviewUnlessResolved(payment, "paymentMethod", pending);
        boolean cashPayment = payment.status() == RESOLVED && payment.candidates().stream()
                .anyMatch(candidate -> "cash".equals(normalize(candidate.code()))
                        || "efectivo".equals(normalize(candidate.code())));
        if ((cashPayment && intent.cashReceived() == null)
                || (intent.cashReceived() != null && intent.cashReceived().signum() < 0)) {
            pending.add("cashReceived");
        }
        return new VoiceSalePreviewDto(List.copyOf(lines), customer, payment, intent.cashReceived(), List.copyOf(pending));
    }

    private Reference resolveItem(String mention) {
        Reference absent = absentReference(mention);
        if (absent != null) {
            return absent;
        }
        SearchResult result = searchItems(mention.trim(), mention);
        if (result.matches().isEmpty() && !result.truncated()) {
            // A plural or shortened mention can suggest alternatives, but cannot become an exact match.
            String suggestionQuery = suggestionQuery(mention);
            if (!suggestionQuery.equalsIgnoreCase(mention.trim())) {
                result = searchItems(suggestionQuery, mention);
            }
        }
        return reference(mention, result);
    }

    private SearchResult searchItems(String query, String mention) {
        var productPage = products.searchProducts(query, null, true, CANDIDATE_PAGE);
        var servicePage = services.searchServices(query, null, true, CANDIDATE_PAGE);
        List<Match> matches = new ArrayList<>();
        productPage.getContent().stream().filter(product -> product.active() && product.id() != null)
                .forEach(product -> matches.add(new Match(new Candidate(PRODUCT, product.id(), product.internalCode(),
                        product.name()), equal(mention, product.name()) || equal(mention, product.internalCode())
                        || equal(mention, product.barcode()))));
        servicePage.getContent().stream().filter(service -> service.active() && service.id() != null)
                .forEach(service -> matches.add(new Match(new Candidate(SERVICE, service.id(), service.code(), service.name()),
                        equal(mention, service.name()) || equal(mention, service.code()))));
        return new SearchResult(matches, productPage.hasNext() || servicePage.hasNext());
    }

    private Reference resolveCustomer(String mention) {
        Reference absent = absentReference(mention);
        if (absent != null) {
            return absent;
        }
        var page = customers.searchCustomers(mention.trim(), CANDIDATE_PAGE);
        List<Match> matches = page.getContent().stream().filter(customer -> customer.active() && customer.id() != null)
                .map(customer -> new Match(customerCandidate(customer), equal(mention, customer.fullName())
                        || equal(mention, customer.identification())))
                .toList();
        return reference(mention, new SearchResult(matches, page.hasNext()));
    }

    private static Candidate customerCandidate(CustomerDto customer) {
        String identification = customer.identification();
        // Never expose the full identity document, email, address or phone in a choice list.
        String distinction = identification != null && identification.length() > 4
                ? "••••" + identification.substring(identification.length() - 4) : "#" + customer.id();
        return new Candidate(CUSTOMER, customer.id(), null,
                customer.fullName() + " (" + distinction + "; #" + customer.id() + ")");
    }

    private Reference resolvePayment(String mention) {
        Reference absent = absentReference(mention);
        if (absent != null) {
            return absent;
        }
        String normalized = normalize(mention);
        List<Match> matches = settings.getActiveValuesByTopic("PAYMENT_METHODS").stream()
                .filter(value -> value.active() && "PAYMENT_METHODS".equals(value.topicCode()))
                .filter(value -> normalize(value.code()).contains(normalized) || normalize(value.label()).contains(normalized))
                .map(value -> new Match(new Candidate(PAYMENT_METHOD, value.id(), value.code(), value.label()),
                        equal(mention, value.code()) || equal(mention, value.label())))
                .toList();
        return reference(mention, new SearchResult(matches, false));
    }

    private static Reference reference(String mention, SearchResult result) {
        LinkedHashMap<String, Match> distinct = new LinkedHashMap<>();
        for (Match match : result.matches()) {
            Candidate candidate = match.candidate();
            distinct.putIfAbsent(candidate.kind() + ":" + candidate.id(), match);
        }
        List<Match> matches = List.copyOf(distinct.values());
        List<Candidate> exact = matches.stream().filter(Match::exact).map(Match::candidate).toList();
        if (exact.size() > 1) {
            return new Reference(mention, AMBIGUOUS, candidates(matches));
        }
        if (result.truncated()) {
            return new Reference(mention, NEEDS_REVIEW, candidates(matches));
        }
        if (exact.size() == 1) {
            return new Reference(mention, RESOLVED, exact);
        }
        var status = matches.isEmpty() ? NOT_FOUND : matches.size() > 1 ? AMBIGUOUS : NEEDS_REVIEW;
        return new Reference(mention, status, candidates(matches));
    }

    private static List<Candidate> candidates(List<Match> matches) {
        return matches.stream().map(Match::candidate).toList();
    }

    private static Reference absentReference(String mention) {
        if (mention == null || mention.isBlank()) {
            return new Reference(mention, NOT_PROVIDED, List.of());
        }
        // Facade searches use SQL LIKE. Do not let model-supplied wildcards enumerate a tenant's catalog.
        if (mention.indexOf('%') >= 0 || mention.indexOf('_') >= 0 || mention.indexOf('\\') >= 0
                || mention.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return new Reference(mention, NEEDS_REVIEW, List.of());
        }
        return null;
    }

    private static String suggestionQuery(String mention) {
        String word = mention.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (word.length() > 3 && word.endsWith("s")) {
            word = word.substring(0, word.length() - 1);
        }
        return word.length() >= 3 ? word : mention.trim();
    }

    private static boolean equal(String mention, String value) {
        return value != null && !value.isBlank() && normalize(mention).equals(normalize(value));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static void reviewUnlessResolved(Reference reference, String path, List<String> pending) {
        if (reference.status() != RESOLVED) {
            pending.add(path);
        }
    }

    private record Match(Candidate candidate, boolean exact) {}
    private record SearchResult(List<Match> matches, boolean truncated) {}
}
