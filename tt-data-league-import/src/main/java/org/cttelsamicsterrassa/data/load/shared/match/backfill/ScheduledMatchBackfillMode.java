package org.cttelsamicsterrassa.data.load.shared.match.backfill;

/**
 * A dedicated mode for the FEAT-00078 backfill, kept separate from
 * {@link org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode} because the
 * backfill is not a consolidation.
 */
public enum ScheduledMatchBackfillMode {
    WRITE,
    REPORT
}
