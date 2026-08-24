package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

public record ClubNameComparison(
        ClubNameMatchClass classification,
        double score,
        String leftKey,
        String rightKey
) {
    public boolean exact() {
        return classification == ClubNameMatchClass.EXACT;
    }

    public boolean fuzzyCandidate() {
        return classification == ClubNameMatchClass.FUZZY_ACCEPTED;
    }
}
