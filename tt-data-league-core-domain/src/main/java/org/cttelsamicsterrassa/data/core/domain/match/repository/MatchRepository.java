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
     * is what makes re-importing a season idempotent.
     */
    Optional<Match> findMatchByNaturalKey(String competition,
                                          Season season,
                                          int groupNumber,
                                          int round,
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
}
