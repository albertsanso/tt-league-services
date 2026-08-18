package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlayerNameMatcher {

    public static final double FUZZY_ACCEPTANCE_THRESHOLD = 0.85;
    public static final int MIN_SIGNIFICANT_TOKENS = 2;

    private final PlayerNameNormalizer normalizer;

    public PlayerNameMatcher(PlayerNameNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    public String exactKey(String name) {
        return normalizer.exactKey(name);
    }

    public List<String> significantTokens(String name) {
        return normalizer.significantTokens(name);
    }

    public String preferredDisplayName(List<String> names) {
        return normalizer.preferredDisplayName(names);
    }

    public PlayerNameComparison compare(String left, String right) {
        String leftKey = exactKey(left);
        String rightKey = exactKey(right);
        List<String> leftTokens = significantTokens(left);
        List<String> rightTokens = significantTokens(right);
        if (!leftKey.isEmpty() && leftKey.equals(rightKey)) {
            return new PlayerNameComparison(leftKey, rightKey, leftTokens, rightTokens, 1.0,
                    PlayerNameMatchClass.EXACT);
        }
        if (leftTokens.size() < MIN_SIGNIFICANT_TOKENS || rightTokens.size() < MIN_SIGNIFICANT_TOKENS) {
            return new PlayerNameComparison(leftKey, rightKey, leftTokens, rightTokens, 0.0,
                    PlayerNameMatchClass.REJECTED_SHORT);
        }
        double score = score(leftKey, rightKey);
        if (leftTokens.size() == rightTokens.size() && tokensCompatible(leftTokens, rightTokens)
                && score >= FUZZY_ACCEPTANCE_THRESHOLD) {
            return new PlayerNameComparison(leftKey, rightKey, leftTokens, rightTokens, score,
                    PlayerNameMatchClass.FUZZY_CANDIDATE);
        }
        if (score < FUZZY_ACCEPTANCE_THRESHOLD) {
            return new PlayerNameComparison(leftKey, rightKey, leftTokens, rightTokens, score,
                    PlayerNameMatchClass.REJECTED_BELOW_THRESHOLD);
        }
        return new PlayerNameComparison(leftKey, rightKey, leftTokens, rightTokens, score,
                PlayerNameMatchClass.REJECTED_TOKEN_MISMATCH);
    }

    private static boolean tokensCompatible(List<String> left, List<String> right) {
        List<String> remaining = new ArrayList<>(right);
        for (String token : left) {
            int bestIndex = -1;
            double bestScore = -1;
            for (int i = 0; i < remaining.size(); i++) {
                double candidateScore = score(token, remaining.get(i));
                if (candidateScore >= FUZZY_ACCEPTANCE_THRESHOLD && candidateScore > bestScore) {
                    bestScore = candidateScore;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0) {
                return false;
            }
            remaining.remove(bestIndex);
        }
        return remaining.isEmpty();
    }

    static double score(String left, String right) {
        int max = Math.max(left.length(), right.length());
        return max == 0 ? 1.0 : 1.0 - ((double) levenshtein(left, right) / max);
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) {
            previous[i] = i;
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
