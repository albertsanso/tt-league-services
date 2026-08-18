package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import java.util.List;

public record ClubNameComparison(
        String leftExactKey,
        String rightExactKey,
        List<String> leftTokens,
        List<String> rightTokens,
        double score,
        ClubNameMatchClass classification
) {
    public boolean exact() {
        return classification == ClubNameMatchClass.EXACT;
    }

    public boolean fuzzyCandidate() {
        return classification == ClubNameMatchClass.FUZZY_CANDIDATE;
    }
}
