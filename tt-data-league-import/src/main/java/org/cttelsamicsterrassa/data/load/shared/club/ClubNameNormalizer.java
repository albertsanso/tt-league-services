package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source-aware club-name folding used by exact grouping and fuzzy comparison.
 *
 * <p>The abbreviation registry only drops tokens demonstrated by fixtures. {@code tt} is a trailing
 * table-tennis suffix in RFETM/FCTT names such as {@code HORTITEC ALZIRA TT}. {@code ctt} is kept:
 * BCNESA uses it as part of the official name ({@code CTT ATENEU} vs {@code CTT DELS HORTS}).</p>
 */
public final class ClubNameNormalizer {

    private static final Set<String> DROPPED_TOKENS = Set.of("tt");
    private static final Set<String> TEAM_LETTERS = Set.of("a", "b", "c");
    private static final Set<String> BCNESA_CATEGORIES = Set.of("sen", "vet");
    private static final Set<String> OPTIONAL_PARTICLES = Set.of("de", "els");

    private static final Map<ImportSource, Set<String>> SOURCE_DROPPED_TOKENS = Map.of(
            ImportSource.FCTT, DROPPED_TOKENS,
            ImportSource.RFETM, DROPPED_TOKENS,
            ImportSource.BCNESA, Set.of()
    );

    public String exactKey(ImportSource source, String name) {
        return parts(source, name).identityKey();
    }

    public List<String> significantTokens(ImportSource source, String name) {
        return parts(source, name).identityTokens();
    }

    public ClubNameParts parts(ImportSource source, String name) {
        if (name == null) {
            return new ClubNameParts(List.of(), List.of(), List.of());
        }
        String folded = fold(name);
        if (folded.isEmpty()) {
            return new ClubNameParts(List.of(), List.of(), List.of());
        }
        Set<String> dropped = SOURCE_DROPPED_TOKENS.getOrDefault(source, Set.of());
        List<String> foldedTokens = new ArrayList<>(List.of(folded.split(" ")));
        List<ClubNameRule> rules = new ArrayList<>();
        List<String> identity = new ArrayList<>(foldedTokens);

        if (source == ImportSource.BCNESA) {
            expandAbbreviations(identity, rules);
            removeTerminalQualifiers(identity, rules);
            removeKnownVenueSuffix(identity, rules);
            removeKnownSponsorPrefix(identity, rules);
            applyCuratedAliases(identity, rules);
            if (identity.size() > 2) {
                boolean removedParticle = identity.removeIf(OPTIONAL_PARTICLES::contains);
                if (removedParticle) {
                    rules.add(ClubNameRule.OPTIONAL_PARTICLE);
                }
            }
        }

        identity.removeIf(dropped::contains);
        if (!identity.equals(foldedTokens) && !rules.contains(ClubNameRule.SPONSOR_PREFIX)
                && !rules.contains(ClubNameRule.CURATED_ALIAS)) {
            // The long-standing FCTT/RFETM TT suffix rule predates this policy.
        }
        identity.removeIf(token -> !isSignificant(token, false));
        return new ClubNameParts(foldedTokens, identity, rules);
    }

    public String preferredDisplayName(ImportSource source, List<String> names) {
        List<ClubNameParts> parts = names.stream().map(name -> parts(source, name)).toList();
        if (source == ImportSource.BCNESA
                && parts.stream().map(ClubNameParts::identityKey).distinct().count() == 1
                && parts.stream().flatMap(part -> part.appliedRules().stream())
                .anyMatch(rule -> rule == ClubNameRule.SPONSOR_PREFIX || rule == ClubNameRule.CURATED_ALIAS)) {
            return parts.getFirst().identityTokens().stream().map(String::toUpperCase).collect(java.util.stream.Collectors.joining(" "));
        }
        if (!names.isEmpty()
                && parts.stream().allMatch(part -> part.appliedRules().contains(ClubNameRule.TEAM_LETTER))) {
            return removeTerminalQualifierSuffix(names.getFirst());
        }
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator
                        .comparingInt((String name) -> canonicalPenalty(parts(source, name)))
                        .thenComparing((String name) -> containsExpandedSant(name) ? 0 : 1)
                        .thenComparing((String name) -> containsParticleDe(name) ? 0 : 1)
                        .thenComparing((String name) -> -occurrences(names, name))
                        .thenComparing(ClubNameNormalizer::fold))
                .findFirst()
                .orElseThrow();
    }

    private static String removeTerminalQualifierSuffix(String name) {
        return name
                .replaceFirst("\\s*-\\s*(?:(?:Sen|Vet)\\s+[A-C]|[A-C])\\s*-?\\s*$", "")
                .replaceFirst("\\s*(?:Sen|Vet)\\s+[A-C]\\s*$", "")
                .replaceFirst("\\s+[A-C]\\s*$", "")
                .stripTrailing();
    }

    private static void expandAbbreviations(List<String> tokens, List<ClubNameRule> rules) {
        for (int i = 0; i < tokens.size(); i++) {
            if ("st".equals(tokens.get(i))) {
                tokens.set(i, "sant");
                rules.add(ClubNameRule.ABBREVIATION);
            }
        }
    }

    private static void removeTerminalQualifiers(List<String> tokens, List<ClubNameRule> rules) {
        boolean removed;
        do {
            removed = false;
            if (!tokens.isEmpty() && TEAM_LETTERS.contains(tokens.getLast())) {
                tokens.removeLast();
                rules.add(ClubNameRule.TEAM_LETTER);
                removed = true;
            }
            if (!tokens.isEmpty() && BCNESA_CATEGORIES.contains(tokens.getLast())) {
                tokens.removeLast();
                rules.add(ClubNameRule.CATEGORY);
                removed = true;
            }
        } while (removed);
    }

    private static void removeKnownVenueSuffix(List<String> tokens, List<ClubNameRule> rules) {
        if (tokens.size() > 3 && tokens.get(tokens.size() - 2).equals("la") && tokens.getLast().equals("cassola")) {
            tokens.removeLast();
            tokens.removeLast();
            rules.add(ClubNameRule.VENUE_SUFFIX);
        }
    }

    private static void removeKnownSponsorPrefix(List<String> tokens, List<ClubNameRule> rules) {
        int marker = tokens.indexOf("tt");
        if (marker > 0 && marker < tokens.size() - 1 && tokens.subList(0, marker).equals(List.of("anecblau"))) {
            tokens.subList(0, marker).clear();
            rules.add(ClubNameRule.SPONSOR_PREFIX);
        }
    }

    private static void applyCuratedAliases(List<String> tokens, List<ClubNameRule> rules) {
        if (tokens.equals(List.of("tt", "els", "joves"))) {
            tokens.clear();
            tokens.addAll(List.of("tt", "joves", "ctdfels"));
            rules.add(ClubNameRule.CURATED_ALIAS);
        }
    }

    private static boolean isSignificant(String token, boolean ignored) {
        return token.length() >= 2 || Character.isDigit(token.charAt(0));
    }

    private static boolean containsExpandedSant(String name) {
        return List.of(fold(name).split(" ")).contains("sant");
    }

    private static boolean containsParticleDe(String name) {
        return List.of(fold(name).split(" ")).contains("de");
    }

    private static int canonicalPenalty(ClubNameParts parts) {
        return (int) parts.appliedRules().stream()
                .filter(rule -> rule == ClubNameRule.TEAM_LETTER
                        || rule == ClubNameRule.CATEGORY
                        || rule == ClubNameRule.SPONSOR_PREFIX
                        || rule == ClubNameRule.VENUE_SUFFIX
                        || rule == ClubNameRule.CURATED_ALIAS)
                .count();
    }

    private static long occurrences(List<String> names, String candidate) {
        String foldedCandidate = fold(candidate);
        return names.stream().filter(foldedCandidate::equals).count();
    }

    private static String fold(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        StringBuilder builder = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char ch = decomposed.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            } else if (Character.isWhitespace(ch)) {
                builder.append(' ');
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
