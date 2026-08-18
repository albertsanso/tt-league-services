package org.cttelsamicsterrassa.data.load.shared.player;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlayerNameNormalizer {

    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "jr", "junior",
            "sr", "senior",
            "st", "saint"
    );

    public String exactKey(String name) {
        return significantTokens(name).stream().sorted().collect(Collectors.joining(" "));
    }

    public List<String> significantTokens(String name) {
        if (name == null) {
            return List.of();
        }
        String folded = fold(name);
        if (folded.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(folded.split(" "))
                .map(token -> ABBREVIATIONS.getOrDefault(token, token))
                .filter(token -> token.length() > 1 || token.chars().allMatch(Character::isDigit))
                .toList();
    }

    public String preferredDisplayName(List<String> names) {
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator
                        .comparingInt((String name) -> significantTokens(name).size())
                        .thenComparing(PlayerNameNormalizer::fold))
                .findFirst()
                .orElseThrow();
    }

    private static String fold(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char character = decomposed.charAt(i);
            if (Character.getType(character) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            } else {
                result.append(' ');
            }
        }
        return result.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
