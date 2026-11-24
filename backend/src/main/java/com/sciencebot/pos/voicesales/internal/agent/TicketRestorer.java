package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind;
import com.sciencebot.pos.voicesales.VoiceTicketDto;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Rebuilds the ticket the POS sent. Only ids and quantities are trusted as intent: names, prices, stock and
 * activity are re-read from the authenticated store's catalog, and anything that no longer holds is dropped with a
 * warning instead of failing the turn.
 */
final class TicketRestorer {
    private final ProductFacade products;
    private final ServiceFacade services;
    private final CustomerFacade customers;
    private final SettingsFacade settings;

    TicketRestorer(ProductFacade products, ServiceFacade services, CustomerFacade customers, SettingsFacade settings) {
        this.products = products;
        this.services = services;
        this.customers = customers;
        this.settings = settings;
    }

    List<String> restore(TicketDraft draft, VoiceAgentRequest.Ticket ticket) {
        List<String> warnings = new ArrayList<>();
        if (ticket == null) {
            return warnings;
        }
        List<VoiceAgentRequest.Line> lines = ticket.lines() == null ? List.of() : ticket.lines();
        if (lines.size() > VoiceAgentRequest.MAX_LINES) {
            warnings.add("El ticket tiene demasiadas líneas; solo se consideraron las primeras "
                    + VoiceAgentRequest.MAX_LINES + ".");
        }
        lines.stream().limit(VoiceAgentRequest.MAX_LINES).forEach(line -> restoreLine(draft, line, warnings));
        restoreCustomer(draft, ticket.customerId(), warnings);
        restorePayment(draft, ticket.paymentMethodCode(), warnings);
        restoreCash(draft, ticket.cashReceived(), warnings);
        return warnings;
    }

    private void restoreLine(TicketDraft draft, VoiceAgentRequest.Line line, List<String> warnings) {
        Integer quantity = line == null ? null : line.quantity();
        if (line == null || line.id() == null || quantity == null || quantity < 1
                || quantity > TicketTools.MAX_QUANTITY || !isKnownKind(line.kind())) {
            warnings.add("Se ignoró una línea del ticket con datos inválidos.");
            return;
        }
        VoiceTicketDto.Line restored = Kind.SERVICE.name().equals(line.kind())
                ? service(line.id(), quantity) : product(line.id(), quantity, warnings);
        if (restored == null) {
            warnings.add("Se quitó del ticket un artículo que ya no está disponible.");
            return;
        }
        var existing = draft.line(restored.kind(), restored.id());
        int total = Math.min(TicketTools.MAX_QUANTITY, restored.quantity() + (existing == null ? 0 : existing.quantity()));
        draft.putLine(new VoiceTicketDto.Line(restored.kind(), restored.id(), restored.code(), restored.name(),
                restored.unitPrice(), total));
    }

    private static boolean isKnownKind(String kind) {
        return Kind.PRODUCT.name().equals(kind) || Kind.SERVICE.name().equals(kind);
    }

    private VoiceTicketDto.Line product(Long id, int quantity, List<String> warnings) {
        Optional<ProductDto> found = safe(() -> products.getById(id)).filter(p -> p.active() && p.salePrice() != null);
        if (found.isEmpty()) {
            return null;
        }
        ProductDto product = found.get();
        int available = product.quantityAvailable();
        if (available < 1) {
            return null;
        }
        int capped = Math.min(quantity, available);
        if (capped < quantity) {
            warnings.add("De " + product.name() + " solo hay " + available + " disponibles; la cantidad se ajustó.");
        }
        return new VoiceTicketDto.Line(Kind.PRODUCT.name(), product.id(), product.internalCode(), product.name(),
                product.salePrice(), capped);
    }

    private VoiceTicketDto.Line service(Long id, int quantity) {
        Optional<ServiceDto> found = safe(() -> services.getById(id)).filter(s -> s.active() && s.price() != null);
        return found.map(s -> new VoiceTicketDto.Line(Kind.SERVICE.name(), s.id(), s.code(), s.name(), s.price(), quantity))
                .orElse(null);
    }

    private void restoreCustomer(TicketDraft draft, Long customerId, List<String> warnings) {
        if (customerId == null) {
            return;
        }
        Optional<CustomerDto> found = safe(() -> customers.getById(customerId)).filter(CustomerDto::active);
        if (found.isEmpty()) {
            warnings.add("El cliente del ticket ya no está disponible; se usa Cliente General.");
            return;
        }
        draft.customer(new VoiceTicketDto.Customer(found.get().id(), found.get().fullName()));
    }

    private void restorePayment(TicketDraft draft, String code, List<String> warnings) {
        if (code == null || code.isBlank()) {
            return;
        }
        var found = settings.getActiveValuesByTopic("PAYMENT_METHODS").stream()
                .filter(v -> v.active() && v.code() != null && v.code().equalsIgnoreCase(code.trim())).findFirst();
        if (found.isEmpty()) {
            warnings.add("El medio de pago del ticket ya no está disponible.");
            return;
        }
        draft.paymentMethod(new VoiceTicketDto.PaymentMethod(found.get().code(), found.get().label()));
    }

    private static void restoreCash(TicketDraft draft, BigDecimal cash, List<String> warnings) {
        if (cash == null) {
            return;
        }
        if (cash.signum() < 0 || cash.compareTo(SaleIntent.MAX_CASH) > 0 || cash.stripTrailingZeros().scale() > 2) {
            warnings.add("Se ignoró un efectivo recibido inválido.");
            return;
        }
        draft.cashReceived(cash);
    }

    /** Facades throw for ids of another store or ones that vanished; both mean "not available here". */
    private static <T> Optional<T> safe(Supplier<Optional<T>> lookup) {
        try {
            return lookup.get();
        } catch (RuntimeException notAvailable) {
            return Optional.empty();
        }
    }
}
