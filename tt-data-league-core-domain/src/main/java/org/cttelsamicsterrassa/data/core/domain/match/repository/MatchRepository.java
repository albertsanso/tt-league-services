package org.cttelsamicsterrassa.data.core.domain.match.repository;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
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

    void saveMatch(Match match);
}
