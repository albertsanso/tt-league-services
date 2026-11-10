package org.cttelsamicsterrassa.data.load.shared.match.backfill;

/**
 * Mode of the FEAT-00121 region backfill. {@code REPORT} performs the same analysis as {@code WRITE}
 * and writes nothing; the backfill is opt-in and never runs by default.
 */
public enum MatchRegionBackfillMode {
    WRITE,
    REPORT
}
