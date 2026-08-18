package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

public enum ClubNameMatchClass {
    EXACT,
    FUZZY_CANDIDATE,
    REJECTED_SHORT,
    REJECTED_TOKEN_MISMATCH,
    REJECTED_BELOW_THRESHOLD
}
