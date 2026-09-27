package org.cttelsamicsterrassa.data.load.shared.classify;

/**
 * The outcome of classifying an {@link org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta}
 * for import.
 */
public enum ActaCompleteness {

    /** Written with its children: games, lineups, and result. */
    PLAYED,

    /** Stored as a scheduled fixture; no result is written. */
    PENDING,

    /** Kept scheduled and reported; not enough of the acta is complete to treat it as PLAYED. */
    PARTIAL,

    /** Reported and not written. */
    INVALID
}
