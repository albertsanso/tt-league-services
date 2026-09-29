package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.PlayerLocation;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl.DoublesPairRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.mapper.DoublesPairToDoublesPairJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.impl.GameRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameToGameJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.impl.LineupRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.mapper.LineupToLineupJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchJPAToMatchMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchToMatchJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.impl.SetScoreRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.mapper.SetScoreToSetScoreJPAMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

@Transactional
@Component
@AllArgsConstructor
public class MatchRepositoryJpa implements MatchRepository {

    private final MatchRepositoryHelper matchRepositoryHelper;
    private final MatchJPAToMatchMapper matchJPAToMatchMapper;
    private final MatchToMatchJPAMapper matchToMatchJPAMapper;
    private final LineupRepositoryHelper lineupRepositoryHelper;
    private final GameRepositoryHelper gameRepositoryHelper;
    private final SetScoreRepositoryHelper setScoreRepositoryHelper;
    private final DoublesPairRepositoryHelper doublesPairRepositoryHelper;
    private final LineupToLineupJPAMapper lineupToLineupJPAMapper;
    private final GameToGameJPAMapper gameToGameJPAMapper;
    private final SetScoreToSetScoreJPAMapper setScoreToSetScoreJPAMapper;
    private final DoublesPairToDoublesPairJPAMapper doublesPairToDoublesPairJPAMapper;

    @Override
    public Optional<Match> findMatchById(UUID id) {
        return matchRepositoryHelper.findById(id).map(matchJPAToMatchMapper);
    }

    @Override
    public Optional<Match> findMatchByExternalId(String externalId) {
        return matchRepositoryHelper.findByExternalId(externalId).map(matchJPAToMatchMapper);
    }

    @Override
    public Optional<Match> findMatchByNaturalKey(String competition,
                                                 Season season,
                                                 Integer groupNumber,
                                                 int round,
                                                 String phase,
                                                 UUID homeTeamId,
                                                 UUID awayTeamId) {
        return matchRepositoryHelper
                .findByCompetitionAndSeasonAndGroupNumberAndRoundAndPhaseAndHomeTeam_IdAndAwayTeam_Id(
                        competition, season.toString(), groupNumber, round, phase, homeTeamId, awayTeamId)
                .map(matchJPAToMatchMapper);
    }

    @Override
    public Optional<Match> findBySourceFixtureId(ImportSource source, String sourceFixtureId) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceFixtureId, "sourceFixtureId");
        return matchRepositoryHelper.findBySourceAndSourceFixtureId(Source.valueOf(source.name()), sourceFixtureId)
                .map(matchJPAToMatchMapper);
    }

    @Override
    public List<Match> findAllMatchesByTeamIds(Collection<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return matchRepositoryHelper.findAllByTeamIds(teamIds)
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    @Override
    public List<Match> findAllMatchesByTeamIdsAndSource(Collection<UUID> teamIds, ImportSource source) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return matchRepositoryHelper.findAllByTeamIdsAndSource(
                        teamIds, source == null ? null : Source.valueOf(source.name()))
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    @Override
    public List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
            Collection<UUID> teamIds,
            ImportSource source,
            Season season,
            String competition) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return matchRepositoryHelper.findAllByTeamIdsAndSourceAndSeasonAndCompetition(
                        teamIds,
                        source == null ? null : Source.valueOf(source.name()),
                        season == null ? null : season.toString(),
                        competition)
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    @Override
    public void saveMatch(Match match) {
        matchRepositoryHelper.save(matchToMatchJPAMapper.apply(match));
    }

    @Override
    public void saveMatches(Collection<Match> matches) {
        matchRepositoryHelper.saveAll(matches.stream().map(matchToMatchJPAMapper).toList());
    }

    @Override
    public List<Match> searchMatches(MatchSearchCriteria criteria) {
        String[] playerNameFragments = nameFragments(criteria.playerName());
        String[] clubNameFragments = nameFragments(criteria.clubName());
        return matchRepositoryHelper.search(Source.valueOf(criteria.source().name()),
                        criteria.season().toString(), criteria.competition(), criteria.fromDate(), criteria.toDate(),
                        criteria.playerId(),
                        criteria.playerLocation() == null ? PlayerLocation.EITHER.name() : criteria.playerLocation().name(),
                        playerNameFragments[0], playerNameFragments[1], playerNameFragments[2],
                        playerNameFragments[3], playerNameFragments[4],
                        clubNameFragments[0], clubNameFragments[1], clubNameFragments[2],
                        clubNameFragments[3], clubNameFragments[4], toJpaStatus(criteria.status()),
                        PageRequest.of(criteria.page(), criteria.pageSize()))
                .stream().map(matchJPAToMatchMapper).toList();
    }

    @Override
    public long countMatches(MatchSearchCriteria criteria) {
        String[] playerNameFragments = nameFragments(criteria.playerName());
        String[] clubNameFragments = nameFragments(criteria.clubName());
        return matchRepositoryHelper.countSearch(Source.valueOf(criteria.source().name()),
                criteria.season().toString(), criteria.competition(), criteria.fromDate(), criteria.toDate(),
                criteria.playerId(),
                criteria.playerLocation() == null ? PlayerLocation.EITHER.name() : criteria.playerLocation().name(),
                playerNameFragments[0], playerNameFragments[1], playerNameFragments[2],
                playerNameFragments[3], playerNameFragments[4],
                clubNameFragments[0], clubNameFragments[1], clubNameFragments[2],
                clubNameFragments[3], clubNameFragments[4], toJpaStatus(criteria.status()));
    }

    private static MatchStatus toJpaStatus(
            org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus status) {
        return status == null
                ? MatchStatus.PLAYED
                : MatchStatus.valueOf(status.name());
    }

    @Override
    public List<Match> findAllMatchesByFragmentsInName(List<String> fragments, int limit) {
        String[] nameFragments = new String[MAX_NAME_FRAGMENTS];
        java.util.Arrays.fill(nameFragments, "");
        if (fragments != null) {
            for (int i = 0; i < fragments.size() && i < MAX_NAME_FRAGMENTS; i++) {
                nameFragments[i] = fragments.get(i);
            }
        }
        return matchRepositoryHelper.searchByFragmentsInName(
                        nameFragments[0], nameFragments[1], nameFragments[2], nameFragments[3], nameFragments[4],
                        PageRequest.of(0, limit))
                .stream().map(matchJPAToMatchMapper).toList();
    }

    /**
     * Splits a free-text search term into up to {@value #MAX_NAME_FRAGMENTS} whitespace-separated
     * fragments so the search can match only when ALL fragments are found (e.g. "oscar campos"
     * matches a match containing "oscar" and "campos", each possibly in a different field: home/away
     * club name or a lineup player's name). Unused slots are empty strings, which the matching query
     * treats as "no fragment" (vacuously satisfied) rather than "match everything".
     */
    private static final int MAX_NAME_FRAGMENTS = 5;

    private static String[] nameFragments(String value) {
        String[] fragments = new String[MAX_NAME_FRAGMENTS];
        java.util.Arrays.fill(fragments, "");
        if (value == null || value.isBlank()) {
            return fragments;
        }
        String[] parts = value.trim().split("\\s+");
        for (int i = 0; i < parts.length && i < MAX_NAME_FRAGMENTS; i++) {
            fragments[i] = parts[i];
        }
        return fragments;
    }

    @Override
    public List<Match> findAllMatchesBySource(ImportSource source) {
        if (source == null) {
            return List.of();
        }
        return matchRepositoryHelper.findAllBySource(Source.valueOf(source.name())).stream()
                .map(matchJPAToMatchMapper).toList();
    }

    @Override
    public List<String> findAllSeasonsBySource(ImportSource source) {
        if (source == null) {
            return List.of();
        }
        return matchRepositoryHelper.findAllSeasonsBySource(Source.valueOf(source.name()));
    }

    @Override
    public List<String> findAllCompetitionsBySourceAndSeason(ImportSource source, Season season) {
        if (source == null || season == null) {
            return List.of();
        }
        return matchRepositoryHelper.findAllCompetitionsBySourceAndSeason(
                Source.valueOf(source.name()), season.toString());
    }

    @Override
    public long countAllMatches() {
        return matchRepositoryHelper.countAllPlayed();
    }

    @Override
    public List<String> findAllSeasons() {
        return matchRepositoryHelper.findAllSeasons();
    }

    @Override
    public long countMatchesBySeason(Season season) {
        if (season == null) {
            return 0;
        }
        return matchRepositoryHelper.countBySeason(season.toString());
    }

    /**
     * FEAT-00080. One transaction (class-level {@code @Transactional}): validates existence and
     * natural key before any delete, removes the old children in FK order, overwrites the header
     * keeping the id, inserts the new children, and flushes so constraint failures roll the whole
     * replacement back rather than leaving a half-written match.
     */
    @Override
    public void replaceMatchContent(MatchContent content) {
        Match replacement = content.match();
        MatchJPA existing = matchRepositoryHelper.findById(replacement.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot replace content of unknown match " + replacement.getId()));
        if (!matchJPAToMatchMapper.apply(existing).hasSameNaturalKeyAs(replacement)) {
            throw new IllegalStateException(
                    "Replacement must not change the natural key of match " + replacement.getId());
        }

        // Flush first: the bulk deletes only auto-flush pending writes touching their own tables,
        // and clearAutomatically would purge not-yet-written parent rows (match, teams).
        matchRepositoryHelper.flush();
        List<UUID> matchIds = List.of(replacement.getId());
        doublesPairRepositoryHelper.deleteAllByMatchIds(matchIds);
        setScoreRepositoryHelper.deleteAllByMatchIds(matchIds);
        gameRepositoryHelper.deleteAllByMatchIds(matchIds);
        lineupRepositoryHelper.deleteAllByMatchIds(matchIds);

        matchRepositoryHelper.save(matchToMatchJPAMapper.apply(replacement));
        lineupRepositoryHelper.saveAll(
                content.lineups().stream().map(lineupToLineupJPAMapper).toList());
        gameRepositoryHelper.saveAll(
                content.games().stream().map(gameToGameJPAMapper).toList());
        setScoreRepositoryHelper.saveAll(
                content.setScores().stream().map(setScoreToSetScoreJPAMapper).toList());
        doublesPairRepositoryHelper.saveAll(
                content.doublesPairs().stream().map(doublesPairToDoublesPairJPAMapper).toList());
        matchRepositoryHelper.flush();
    }

    @Override
    public void updateSchedule(UUID matchId, MatchSchedule schedule) {
        java.time.LocalDate matchDate = schedule.dateTime() == null ? null : schedule.dateTime().toLocalDate();
        java.time.LocalTime matchTime = schedule.dateTime() == null ? null : schedule.dateTime().toLocalTime();
        int updated = matchRepositoryHelper.updateScheduleOfScheduledMatch(
                matchId, matchDate, matchTime, schedule.city(), schedule.venue(),
                schedule.refereeName(), schedule.refereeLicense());
        if (updated == 0) {
            if (!matchRepositoryHelper.existsById(matchId)) {
                throw new IllegalStateException("Cannot reschedule unknown match " + matchId);
            }
            throw new IllegalStateException(
                    "Only SCHEDULED matches can be rescheduled, match " + matchId + " is PLAYED");
        }
    }

    /**
     * FEAT-00084. Reads the grouped per-round/per-status counts of one source and season and hands
     * them to the domain calculator: the current-round and last-complete-round rule is never
     * duplicated in JPQL. Read-only, and informational for callers - it is not an import-skip signal.
     */
    @Override
    public List<RoundProgress> findRoundProgress(ImportSource source, Season season) {
        return RoundProgressCalculator.compute(source, season, findRoundStatusCounts(source, season));
    }

    /**
     * FEAT-00088. Reads the grouped per-round/per-status counts of one source and season and maps
     * them to domain {@link RoundStatusCount} rows. Read-only; the progress definition lives in
     * {@link RoundProgressCalculator}, never in JPQL. Exposed so the import preview can project
     * progress over modified counts without re-implementing the grouping.
     */
    @Override
    public List<RoundStatusCount> findRoundStatusCounts(ImportSource source, Season season) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        return matchRepositoryHelper
                .countByRoundAndStatus(Source.valueOf(source.name()), season.toString())
                .stream()
                .map(MatchRepositoryJpa::toStatusCount)
                .toList();
    }

    /**
     * FEAT-00086. Source-scoped read of every match with one status, for snapshot reconciliation.
     * Read-only; no entity, index or column change.
     */
    @Override
    public List<Match> findMatchesBySourceSeasonAndStatus(
            ImportSource source, Season season,
            org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus status) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(status, "status");
        return matchRepositoryHelper
                .findAllBySourceAndSeasonAndStatus(Source.valueOf(source.name()), season.toString(),
                        MatchStatus.valueOf(status.name()))
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    private static RoundStatusCount toStatusCount(RoundStatusCountProjection projection) {
        return new RoundStatusCount(projection.competition(), projection.groupNumber(), projection.phase(),
                projection.round(),
                org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus.valueOf(projection.status().name()),
                projection.matches());
    }
}
