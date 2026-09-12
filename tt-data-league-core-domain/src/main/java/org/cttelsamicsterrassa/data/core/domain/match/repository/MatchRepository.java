package org.cttelsamicsterrassa.data.core.domain.match.repository;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link Match} aggregate root.
 */
public interface MatchRepository {

    Optional<Match> findMatchById(UUID id);
    Optional<Match> findMatchByExternalId(String externalId);

    /**
     * Finds a match by its natural key. Competition, season, group and round alone do not identify
     * a match — a round holds one match per pair of clubs — so both clubs are part of the key. This
     * is what makes re-importing a season idempotent. Phase is also part of the key because some
     * sources (e.g. BCNESA) reuse round numbers across phases within the same group. {@code groupNumber}
     * may be {@code null} for fixtures with no group (e.g. BCNESA Veterans "Other"-phase fixtures).
     */
    Optional<Match> findMatchByNaturalKey(String competition,
                                          Season season,
                                          Integer groupNumber,
                                          int round,
                                          String phase,
                                          UUID homeTeamId,
                                          UUID awayTeamId);

    /**
     * Returns matches involving any of the supplied canonical team registrations.
     */
    List<Match> findAllMatchesByTeamIds(Collection<UUID> teamIds);

    List<Match> findAllMatchesByTeamIdsAndSource(Collection<UUID> teamIds, ImportSource source);

    List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
            Collection<UUID> teamIds,
            ImportSource source,
            Season season,
            String competition);

    default List<Match> searchMatches(MatchSearchCriteria criteria) {
        return List.of();
    }

    /**
     * Source/season-agnostic free-text lookup by player or club name, capped to {@code limit} results
     * ordered by most recent first. Used by global search, where the query is name-only and does not
     * carry the mandatory source/season scoping that {@link #searchMatches} requires.
     */
    default List<Match> findAllMatchesByFragmentsInName(List<String> fragments, int limit) {
        return List.of();
    }
    default long countMatches(MatchSearchCriteria criteria) {
        return 0;
    }
    default List<Match> findAllMatchesBySource(ImportSource source) {
        return List.of();
    }
    default List<String> findAllSeasonsBySource(ImportSource source) {
        return List.of();
    }
    default List<String> findAllCompetitionsBySourceAndSeason(ImportSource source, Season season) {
        return List.of();
    }

    /**
     * Returns the total number of matches across every source, for community-wide aggregate statistics.
     */
    default long countAllMatches() {
        return 0;
    }

    /**
     * Returns every season with at least one match, across every source, ordered from the most to the
     * least recent. Used to determine the community-wide current season.
     */
    default List<String> findAllSeasons() {
        return List.of();
    }

    /**
     * Returns the number of matches played in the given season, across every source.
     */
    default long countMatchesBySeason(Season season) {
        return 0;
    }

    void saveMatch(Match match);
    default void saveMatches(Collection<Match> matches) {
        matches.forEach(this::saveMatch);
    }
}
