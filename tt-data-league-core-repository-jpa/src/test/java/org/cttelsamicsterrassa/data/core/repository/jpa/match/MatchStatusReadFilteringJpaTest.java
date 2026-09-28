package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00079: proves which read queries exclude SCHEDULED matches (statistics and search) and
 * which deliberately keep them (team-id traversals used by consolidation, risk K9).
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchStatusReadFilteringJpaTest {

    private static final Season SEASON = Season.of(2025);

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void searchAndCountDefaultToPlayedAndHonourAnExplicitScheduledStatus() {
        Match played = storedMatch("FILTER HOME", "FILTER AWAY", ImportSource.RFETM, SEASON, 1, MatchStatus.PLAYED);
        Match scheduled = storedMatch("FILTER HOME 2", "FILTER AWAY 2", ImportSource.RFETM, SEASON, 2,
                MatchStatus.SCHEDULED);

        var defaultCriteria = new MatchSearchCriteria(ImportSource.RFETM, SEASON, null, null, null,
                null, null, null);
        List<UUID> searchIds = matchRepository.searchMatches(defaultCriteria).stream()
                .map(Match::getId).toList();
        assertTrue(searchIds.contains(played.getId()));
        assertFalse(searchIds.contains(scheduled.getId()));
        assertEquals(matchRepository.countMatches(defaultCriteria),
                matchRepository.searchMatches(defaultCriteria).size());

        var scheduledCriteria = new MatchSearchCriteria(ImportSource.RFETM, SEASON, null, null, null,
                null, null, null, null, 0, 10, MatchStatus.SCHEDULED);
        List<UUID> scheduledIds = matchRepository.searchMatches(scheduledCriteria).stream()
                .map(Match::getId).toList();
        assertEquals(List.of(scheduled.getId()), scheduledIds);
        assertEquals(1, matchRepository.countMatches(scheduledCriteria));
    }

    @Test
    void fragmentSearchExcludesScheduledMatches() {
        Match played = storedMatch("FRAGMENTSEARCH HOME", "FRAGMENTSEARCH AWAY",
                ImportSource.RFETM, SEASON, 1, MatchStatus.PLAYED);
        storedMatch("FRAGMENTSEARCH HOME 2", "FRAGMENTSEARCH AWAY 2",
                ImportSource.RFETM, SEASON, 2, MatchStatus.SCHEDULED);

        List<UUID> ids = matchRepository.findAllMatchesByFragmentsInName(
                List.of("fragmentsearch"), 10).stream().map(Match::getId).toList();

        assertEquals(List.of(played.getId()), ids);
    }

    @Test
    void aggregateCountsAndSeasonsCoverPlayedMatchesOnly() {
        storedMatch("AGG HOME", "AGG AWAY", ImportSource.RFETM, SEASON, 1, MatchStatus.PLAYED);
        storedMatch("AGG HOME 2", "AGG AWAY 2", ImportSource.RFETM, SEASON, 2, MatchStatus.SCHEDULED);
        // A season whose only match is scheduled must not surface in aggregate season lists.
        storedMatch("PENDING ONLY HOME", "PENDING ONLY AWAY", ImportSource.BCNESA, Season.of(2022), 1,
                MatchStatus.SCHEDULED);

        assertEquals(1, matchRepository.countMatchesBySeason(SEASON));
        assertEquals(0, matchRepository.countMatchesBySeason(Season.of(2022)));
        assertFalse(matchRepository.findAllSeasons().contains("2022-2023"));
        assertTrue(matchRepository.findAllSeasons().contains(SEASON.toString()));

        long withScheduledStored = matchRepository.countAllMatches();
        matchRepository.saveMatch(storedMatch("AGG HOME 3", "AGG AWAY 3", ImportSource.RFETM,
                SEASON, 3, MatchStatus.PLAYED));
        assertEquals(withScheduledStored + 1, matchRepository.countAllMatches());
    }

    @Test
    void findAllMatchesBySourceExcludesScheduledMatches() {
        Match played = storedMatch("BYSOURCE HOME", "BYSOURCE AWAY", ImportSource.FCTT, SEASON, 1,
                MatchStatus.PLAYED);
        storedMatch("BYSOURCE HOME 2", "BYSOURCE AWAY 2", ImportSource.FCTT, SEASON, 2,
                MatchStatus.SCHEDULED);

        List<UUID> ids = matchRepository.findAllMatchesBySource(ImportSource.FCTT).stream()
                .map(Match::getId).toList();

        assertTrue(ids.contains(played.getId()));
        assertTrue(matchRepository.findAllMatchesBySource(ImportSource.FCTT).stream().allMatch(Match::isPlayed));
    }

    @Test
    void teamIdTraversalsKeepScheduledMatchesForConsolidation() {
        Team home = storedTeam("K9 HOME", SEASON, ImportSource.RFETM);
        Team away = storedTeam("K9 AWAY", SEASON, ImportSource.RFETM);
        Match played = storedMatch(home, away, 1, MatchStatus.PLAYED);
        Match scheduled = storedMatch(home, away, 2, MatchStatus.SCHEDULED);
        UUID homeTeamId = home.getId();

        List<UUID> byTeamIds = matchRepository.findAllMatchesByTeamIds(List.of(homeTeamId)).stream()
                .map(Match::getId).toList();
        assertTrue(byTeamIds.contains(played.getId()));
        assertTrue(byTeamIds.contains(scheduled.getId()));

        List<UUID> byTeamIdsAndSource = matchRepository
                .findAllMatchesByTeamIdsAndSource(List.of(homeTeamId), ImportSource.RFETM).stream()
                .map(Match::getId).toList();
        assertTrue(byTeamIdsAndSource.contains(scheduled.getId()));

        List<UUID> byTeamIdsScoped = matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeamId), ImportSource.RFETM, SEASON, played.getCompetition()).stream()
                .map(Match::getId).toList();
        assertTrue(byTeamIdsScoped.contains(scheduled.getId()));
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match storedMatch(String homeName, String awayName, ImportSource source, Season season,
                              int round, MatchStatus status) {
        return storedMatch(storedTeam(homeName, season, source), storedTeam(awayName, season, source),
                round, status);
    }

    private Match storedMatch(Team home, Team away, int round, MatchStatus status) {
        Match match;
        if (status == MatchStatus.PLAYED) {
            match = Match.builder()
                    .id(UUID.randomUUID())
                    .source(home.getSource())
                    .competition("preferent-status-filter")
                    .season(home.getSeason())
                    .groupNumber(1)
                    .round(round)
                    .homeTeam(home)
                    .awayTeam(away)
                    .homeGamesWon(5)
                    .awayGamesWon(2)
                    .winnerTeam(home)
                    .status(MatchStatus.PLAYED)
                    .createExisting();
        } else {
            match = Match.builder()
                    .id(UUID.randomUUID())
                    .source(home.getSource())
                    .competition("preferent-status-filter")
                    .season(home.getSeason())
                    .groupNumber(1)
                    .round(round)
                    .homeTeam(home)
                    .awayTeam(away)
                    .status(MatchStatus.SCHEDULED)
                    .createExisting();
        }
        matchRepository.saveMatch(match);
        return match;
    }

    private Team storedTeam(String name, Season season, ImportSource source) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), source, name, season, club);
        teamRepository.saveTeam(team);
        return team;
    }
}
