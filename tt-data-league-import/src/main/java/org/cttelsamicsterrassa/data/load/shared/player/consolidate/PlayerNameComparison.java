package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import java.util.List;

public record PlayerNameComparison(
        String leftKey,
        String rightKey,
        List<String> leftTokens,
        List<String> rightTokens,
        double score,
        PlayerNameMatchClass classification
) {
    public boolean fuzzyCandidate() {
        return classification == PlayerNameMatchClass.FUZZY_CANDIDATE;
    }
}
