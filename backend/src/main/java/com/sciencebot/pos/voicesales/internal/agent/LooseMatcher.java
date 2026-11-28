package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.ParameterValueDto;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Candidate;
import org.springframework.data.domain.PageRequest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind.PAYMENT_METHOD;
import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind.PRODUCT;
import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind.SERVICE;

/**
 * Fallback for what a person actually says versus how the catalog spells it: "aceite de girasol de un litro" for
 * "Aceite Girasol 1L", "Neki" for "Nequi". The strict resolver only finds names that contain the whole mention, so
 * the agent tries this when it comes back empty. It never invents anything: it ranks catalog entries by the words
 * they share with the mention and settles only when one entry is clearly the best; otherwise it returns the
 * closest ones so the cashier chooses.
 */
final class LooseMatcher {
    /** Closest entries (best first) and whether the first one is the single clear winner. */
    record Outcome(List<Candidate> best, boolean unique) {
        static final Outcome NONE = new Outcome(List.of(), false);
    }

    private static final PageRequest PAGE = PageRequest.of(0, 20);
    private static final int MAX_BEST = 5;
    private static final Set<String> STOPWORDS = Set.of("de", "del", "la", "el", "los", "las", "un", "una", "unos",
            "unas", "y", "con", "en", "por", "para", "a");
    private static final Pattern TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)?|\\p{L}+");

    private LooseMatcher() {
    }

    static Outcome items(ProductFacade products, ServiceFacade services, String mention) {
        List<String> wanted = tokens(mention);
        if (wanted.isEmpty()) {
            return Outcome.NONE;
        }
        Map<String, Scored> found = new LinkedHashMap<>();
        for (String token : wanted) {
            if (token.length() < 3 || Character.isDigit(token.charAt(0))) {
                continue;
            }
            products.searchProducts(token, null, true, PAGE).getContent().stream()
                    .filter(p -> p.active() && p.id() != null)
                    .forEach(p -> found.putIfAbsent("P" + p.id(),
                            score(new Candidate(PRODUCT, p.id(), p.internalCode(), p.name()), wanted)));
            services.searchServices(token, null, true, PAGE).getContent().stream()
                    .filter(s -> s.active() && s.id() != null)
                    .forEach(s -> found.putIfAbsent("S" + s.id(),
                            score(new Candidate(SERVICE, s.id(), s.code(), s.name()), wanted)));
        }
        return settle(found.values().stream(), wanted.size());
    }

    static Outcome paymentMethod(SettingsFacade settings, String mention) {
        String wanted = String.join("", tokens(mention));
        if (wanted.length() < 4) {
            return Outcome.NONE;
        }
        List<ParameterValueDto> values = settings.getActiveValuesByTopic("PAYMENT_METHODS");
        int limit = 2;
        List<Candidate> close = new ArrayList<>();
        int bestDistance = Integer.MAX_VALUE;
        for (ParameterValueDto value : values) {
            if (!value.active() || !"PAYMENT_METHODS".equals(value.topicCode())) {
                continue;
            }
            int distance = Math.min(distance(wanted, String.join("", tokens(value.label()))),
                    distance(wanted, String.join("", tokens(value.code()))));
            if (distance > limit || distance > bestDistance) {
                continue;
            }
            if (distance < bestDistance) {
                close.clear();
                bestDistance = distance;
            }
            close.add(new Candidate(PAYMENT_METHOD, value.id(), value.code(), value.label()));
        }
        return new Outcome(List.copyOf(close), close.size() == 1);
    }

    private record Scored(Candidate candidate, int shared, int extra) {
    }

    private static Scored score(Candidate candidate, List<String> wanted) {
        List<String> have = tokens(candidate.label());
        int shared = 0;
        for (String word : wanted) {
            if (have.stream().anyMatch(other -> sameWord(word, other))) {
                shared++;
            }
        }
        return new Scored(candidate, shared, Math.max(0, have.size() - shared));
    }

    private static boolean sameWord(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        // "cafes" for "cafe", "gaseosas" for "gaseosa": a plural or a clipped word, never a short prefix.
        return a.length() >= 4 && b.length() >= 4 && (a.startsWith(b) || b.startsWith(a));
    }

    private static Outcome settle(java.util.stream.Stream<Scored> all, int wantedWords) {
        List<Scored> ranked = all.filter(s -> s.shared() > 0)
                .sorted(Comparator.comparingInt(Scored::shared).reversed().thenComparingInt(Scored::extra))
                .toList();
        if (ranked.isEmpty()) {
            return Outcome.NONE;
        }
        Scored top = ranked.getFirst();
        // At least 60% of what was said has to be found; a lone shared word out of many is not a match.
        if (top.shared() * 10 < wantedWords * 6) {
            return Outcome.NONE;
        }
        List<Candidate> best = ranked.stream().filter(s -> s.shared() == top.shared()).limit(MAX_BEST)
                .map(Scored::candidate).toList();
        // A tie is never broken by guessing: the cashier chooses.
        boolean unique = ranked.size() == 1 || ranked.get(1).shared() < top.shared();
        return new Outcome(unique ? List.of(top.candidate()) : best, unique);
    }

    /** Lower-case words and numbers with accents removed and spoken units written as the catalog writes them. */
    static List<String> tokens(String value) {
        if (value == null) {
            return List.of();
        }
        String text = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\bmililitros?\\b", "ml").replaceAll("\\blitros?\\b", "l")
                .replaceAll("\\b(?:kilogramos?|kilos?)\\b", "kg").replaceAll("\\bgramos?\\b", "g")
                .replaceAll("\\bun (l|kg|g|ml)\\b", "1 $1");
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group().replace(',', '.');
            if (!STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[b.length()];
    }
}
