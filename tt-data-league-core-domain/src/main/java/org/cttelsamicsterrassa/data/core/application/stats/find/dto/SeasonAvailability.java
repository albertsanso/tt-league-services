package org.cttelsamicsterrassa.data.core.application.stats.find.dto;

/**
 * Availability of the community-wide current season used by {@link CommunityStatisticsReadModel}.
 */
public enum SeasonAvailability {
    /** A current season was found; it is the most recent season with imported matches. */
    IN_PROGRESS,
    /** No season data is available across any source. */
    UNAVAILABLE
}
