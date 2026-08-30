package org.cttelsamicsterrassa.data.core.application.stats.find.dto;

/**
 * Community-wide statistics overview, aggregated across every import source.
 */
public record CommunityStatisticsReadModel(
        CountSummary players,
        CountSummary clubs,
        CountSummary matches,
        CurrentSeasonSummary season) {

    /**
     * A total count together with the count scoped to the current season.
     */
    public record CountSummary(long total) {
    }

    /**
     * The community-wide current season: the most recent season with imported matches, or an
     * unavailable season when no match data exists for any source.
     */
    public record CurrentSeasonSummary(String name, SeasonAvailability status) {
    }
}
