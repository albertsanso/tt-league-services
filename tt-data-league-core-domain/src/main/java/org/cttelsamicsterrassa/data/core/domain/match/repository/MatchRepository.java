package org.cttelsamicsterrassa.data.core.domain.match.repository;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
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
     * Finds a match by the source-supplied fixture id ({@code id_partido}) captured at import time
     * (FEAT-00083). Exact match on {@code (source, source_fixture_id)}; the unique constraint makes
     * the result at most one row and matches of any status are returned. The lookup is always
     * source-scoped: the same id may exist under another source.
     *
     * @throws NullPointerException if {@code source} or {@code sourceFixtureId} is {@code null}
     */
    Optional<Match> findBySourceFixtureId(ImportSource source, String sourceFixtureId);

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
     * Returns the total number of PLAYED matches across every source, for community-wide aggregate
     * statistics. SCHEDULED fixtures are excluded (FEAT-00079).
     */
    default long countAllMatches() {
        return 0;
    }

    /**
     * Returns every season with at least one PLAYED match, across every source, ordered from the
     * most to the least recent. Used to determine the community-wide current season; seasons whose
     * matches are all SCHEDULED do not appear (FEAT-00079).
     */
    default List<String> findAllSeasons() {
        return List.of();
    }

    /**
     * Returns the number of PLAYED matches in the given season, across every source
     * (FEAT-00079).
     */
    default long countMatchesBySeason(Season season) {
        return 0;
    }

    void saveMatch(Match match);
    default void saveMatches(Collection<Match> matches) {
        matches.forEach(this::saveMatch);
    }

    /**
     * Replaces the whole playable content of an existing match in one transaction (FEAT-00080):
     * the header is overwritten and its lineups, games, set scores and doubles pairs are deleted
     * and re-inserted from {@code content}. The match id is preserved and the natural key
     * (source, competition, season, group, round, phase, teams) must be unchanged. Valid for
     * upgrading a SCHEDULED fixture to PLAYED and for correcting an already PLAYED match; the new
     * content must be PLAYED (a played match is never downgraded here).
     *
     * @throws IllegalStateException if no match with {@code content.match().getId()} exists or its
     *         natural key differs from the replacement
     */
    void replaceMatchContent(MatchContent content);

    /**
     * Records the source content checksum of an already stored PLAYED match (FEAT-00089), leaving
     * every other column untouched. Used when detection is enabled and a legacy PLAYED match has no
     * (current-version) checksum yet: the incoming checksum is adopted as the baseline instead of
     * re-applying the acta. The checksum is only meaningful on a PLAYED match, so a SCHEDULED match
     * is rejected.
     *
     * @throws NullPointerException  if {@code matchId} or {@code sourceChecksum} is {@code null}
     * @throws IllegalStateException if no match with {@code matchId} exists or it is not PLAYED
     */
    void recordSourceChecksum(UUID matchId, String sourceChecksum);

    /**
     * Rewrites the schedule fields (date, time, city, venue, referee name and license) of a
     * SCHEDULED match (FEAT-00080). Touches nothing else: status, teams, results and children are
     * left alone, and the values are written exactly as given.
     *
     * @throws IllegalStateException if no match with {@code matchId} exists or it is not SCHEDULED
     */
    void updateSchedule(UUID matchId, MatchSchedule schedule);

    /**
     * Jornada progress of every competition, group and phase that has stored matches in the given
     * source and season (FEAT-00084): the current round, the last complete round and the
     * scheduled/played match counts.
     *
     * <p>The rule is defined by {@link org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator},
     * which every adapter must reuse: an adapter only reads grouped counts, it never re-implements the
     * definition. The lookup is always source-scoped and counts matches of every status, so an empty
     * list means the source and season hold no matches at all.</p>
     *
     * <p>The result is informational only: it is a read model for operators and must never be used to
     * decide whether an import file is skipped.</p>
     *
     * @throws NullPointerException if {@code source} or {@code season} is {@code null}
     */
    List<RoundProgress> findRoundProgress(ImportSource source, Season season);

    /**
     * The grouped per-round/per-status match counts of one source and season (FEAT-00088): the raw
     * rows {@link org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator}
     * consumes. Exposed so a caller can project progress over modified counts (for example the
     * import preview adding its planned deltas) without re-implementing the grouping. The lookup is
     * always source-scoped and read-only; an empty list means the source and season hold no matches
     * at all.
     *
     * @throws NullPointerException if {@code source} or {@code season} is {@code null}
     */
    List<RoundStatusCount> findRoundStatusCounts(ImportSource source, Season season);

    /**
     * Returns every stored match of one source, season and status (FEAT-00086). Used by snapshot
     * reconciliation, which compares the stored SCHEDULED fixtures of a season against the fixtures
     * seen in a snapshot run. The lookup is always source-scoped and read-only; an empty list means
     * the source and season hold no match with that status.
     *
     * @throws NullPointerException if {@code source}, {@code season} or {@code status} is {@code null}
     */
    List<Match> findMatchesBySourceSeasonAndStatus(ImportSource source, Season season, MatchStatus status);

    /**
     * Returns every stored match of one source, season and competition, regardless of status
     * (FEAT-00092). This is the season-calendar read: it must feed the calendar handler only and
     * never statistics, search, community counts, or any PLAYED-only view (FEAT-00079). The lookup is
     * always source-scoped and read-only; an empty list means the source, season and competition
     * hold no match at all.
     *
     * @throws NullPointerException     if {@code source}, {@code season} or {@code competition} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code competition} is blank
     */
    List<Match> findMatchesBySourceSeasonAndCompetition(ImportSource source, Season season, String competition);
}
