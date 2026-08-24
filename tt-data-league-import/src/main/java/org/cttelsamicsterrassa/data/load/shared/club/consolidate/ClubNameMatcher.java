package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ClubNameMatcher {

    public static final double FUZZY_ACCEPTANCE_THRESHOLD = 0.88d;

    private static final Set<String> STOP_WORDS = Set.of(
            "club", "tennis", "taula", "tenis", "mesa", "de", "del", "dels", "la", "les", "els", "ctt", "tt");

    private final ClubNameNormalizer normalizer;

    public ClubNameMatcher(ClubNameNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public ClubNameComparison compare(ImportSource source, String left, String right) {
        String leftKey = normalizer.exactKey(source, left);
        String rightKey = normalizer.exactKey(source, right);

        if (leftKey.equals(rightKey)) {
            return new ClubNameComparison(ClubNameMatchClass.EXACT, 1.0d, leftKey, rightKey);
        }
        if (leftKey.length() < 3 || rightKey.length() < 3
                || tokenCount(leftKey) < 2 || tokenCount(rightKey) < 2) {
            return new ClubNameComparison(ClubNameMatchClass.REJECTED_SHORT, similarity(leftKey, rightKey), leftKey, rightKey);
        }
        if (!sharesSignificantToken(leftKey, rightKey)) {
            return new ClubNameComparison(
                    ClubNameMatchClass.REJECTED_TOKEN_MISMATCH,
                    similarity(leftKey, rightKey),
                    leftKey,
                    rightKey);
        }

        double score = similarity(leftKey, rightKey);
        if (score >= FUZZY_ACCEPTANCE_THRESHOLD) {
            return new ClubNameComparison(ClubNameMatchClass.FUZZY_ACCEPTED, score, leftKey, rightKey);
        }
        return new ClubNameComparison(ClubNameMatchClass.REJECTED_BELOW_THRESHOLD, score, leftKey, rightKey);
    }

    private static boolean sharesSignificantToken(String left, String right) {
        Set<String> leftTokens = significantTokens(left);
        Set<String> rightTokens = significantTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }
        return leftTokens.stream().anyMatch(rightTokens::contains);
    }

    private static Set<String> significantTokens(String key) {
        return Arrays.stream(key.split("\\s+"))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static int tokenCount(String key) {
        return (int) Arrays.stream(key.split("\\s+"))
                .filter(token -> !token.isBlank())
                .count();
    }

    private static double similarity(String left, String right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0d;
        }
        int distance = levenshteinDistance(left, right);
        int maxLength = Math.max(left.length(), right.length());
        return maxLength == 0 ? 1.0d : 1.0d - ((double) distance / (double) maxLength);
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
