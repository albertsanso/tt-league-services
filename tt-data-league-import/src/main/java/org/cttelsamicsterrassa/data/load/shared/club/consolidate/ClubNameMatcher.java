package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Objects;

/**
 * Immutable matching policy: an exact key plus a classified comparison, never a bare boolean.
 */
public final class ClubNameMatcher {

    public static final double FUZZY_ACCEPTANCE_THRESHOLD = 0.85;
    public static final int MIN_SIGNIFICANT_TOKENS = 2;

    private final ClubNameNormalizer normalizer;

    public ClubNameMatcher(ClubNameNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    public String exactKey(ImportSource source, String name) {
        return normalizer.exactKey(source, name);
    }

    public ClubNameParts parts(ImportSource source, String name) {
        return normalizer.parts(source, name);
    }

    public String preferredDisplayName(ImportSource source, List<String> names) {
        return normalizer.preferredDisplayName(source, names);
    }

    public ClubNameComparison compare(ImportSource source, String left, String right) {
        String leftKey = normalizer.exactKey(source, left);
        String rightKey = normalizer.exactKey(source, right);
        List<String> leftTokens = normalizer.significantTokens(source, left);
        List<String> rightTokens = normalizer.significantTokens(source, right);
        if (leftKey.equals(rightKey) && !leftKey.isEmpty()) {
            return new ClubNameComparison(leftKey, rightKey, leftTokens, rightTokens, 1.0, ClubNameMatchClass.EXACT);
        }
        if (leftTokens.size() < MIN_SIGNIFICANT_TOKENS || rightTokens.size() < MIN_SIGNIFICANT_TOKENS) {
            return new ClubNameComparison(leftKey, rightKey, leftTokens, rightTokens, 0.0, ClubNameMatchClass.REJECTED_SHORT);
        }
        if (leftTokens.size() != rightTokens.size()) {
            return new ClubNameComparison(
                    leftKey, rightKey, leftTokens, rightTokens, score(leftKey, rightKey), ClubNameMatchClass.REJECTED_TOKEN_MISMATCH);
        }
        double score = score(leftKey, rightKey);
        if (tokensCompatible(leftTokens, rightTokens) && score >= FUZZY_ACCEPTANCE_THRESHOLD) {
            return new ClubNameComparison(leftKey, rightKey, leftTokens, rightTokens, score, ClubNameMatchClass.FUZZY_CANDIDATE);
        }
        if (score < FUZZY_ACCEPTANCE_THRESHOLD) {
            return new ClubNameComparison(leftKey, rightKey, leftTokens, rightTokens, score, ClubNameMatchClass.REJECTED_BELOW_THRESHOLD);
        }
        return new ClubNameComparison(
                leftKey, rightKey, leftTokens, rightTokens, score, ClubNameMatchClass.REJECTED_TOKEN_MISMATCH);
    }

    private static boolean tokensCompatible(List<String> left, List<String> right) {
        List<String> remaining = new java.util.ArrayList<>(right);
        for (String token : left) {
            int matchIndex = bestCompatibleIndex(token, remaining);
            if (matchIndex < 0) {
                return false;
            }
            remaining.remove(matchIndex);
        }
        return remaining.isEmpty();
    }

    private static int bestCompatibleIndex(String token, List<String> candidates) {
        int bestIndex = -1;
        double bestScore = -1;
        for (int i = 0; i < candidates.size(); i++) {
            double tokenScore = score(token, candidates.get(i));
            if (tokenScore >= FUZZY_ACCEPTANCE_THRESHOLD && tokenScore > bestScore) {
                bestScore = tokenScore;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static double score(String left, String right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        int max = Math.max(left.length(), right.length());
        if (max == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshtein(left, right) / max);
    }

    static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
