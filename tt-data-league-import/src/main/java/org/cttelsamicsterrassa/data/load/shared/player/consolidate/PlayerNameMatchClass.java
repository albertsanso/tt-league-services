package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

public enum PlayerNameMatchClass {
    EXACT,
    FUZZY_CANDIDATE,
    REJECTED_SHORT,
    REJECTED_TOKEN_MISMATCH,
    REJECTED_BELOW_THRESHOLD
}
