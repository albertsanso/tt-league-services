package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00084: the JPA adapter reads the grouped per-round/per-status counts of one source and season
 * and hands them to the domain calculator. This test pins the query scoping (source, season,
 * competition, group, phase) and the null group/phase rows; the rule itself is tested in the domain.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchRoundProgressJpaTest {

    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "tercera-nacional-masculino";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void groupOneOfTheFcttSeasonIsScopedToItsSourceSeasonAndGroup() {
        storedGroupOneShape();
        // Decoys: the same competition, group and phase under another source and in another season,
        // and another group of this very source and season.
        storedMatch(ImportSource.RFETM, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, Season.of(2025), TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 2, "1a Fase", 1, MatchStatus.PLAYED);

        List<RoundProgress> progress = matchRepository.findRoundProgress(ImportSource.FCTT, SEASON);

        assertEquals(List.of(1, 2), progress.stream().map(RoundProgress::groupNumber).toList(),
                "the other source and the other season do not leak, group 2 is its own row");
        RoundProgress row = progress.getFirst();
        assertEquals(ImportSource.FCTT, row.source());
        assertEquals(SEASON, row.season());
        assertEquals(TERCERA, row.competition());
        assertEquals(1, row.groupNumber());
        assertEquals("1a Fase", row.phase());
        assertEquals(1, row.currentRound());
        assertNull(row.lastCompleteRound());
        assertEquals(9, row.scheduledMatches());
        assertEquals(3, row.playedMatches());
        assertEquals(1, progress.get(1).playedMatches(), "group 2 holds only its own match");
    }

    @Test
    void nullGroupAndNullPhaseAreKeptAsTheirOwnRows() {
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, null, null, 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);

        List<RoundProgress> progress = matchRepository.findRoundProgress(ImportSource.FCTT, SEASON);

        assertEquals(2, progress.size());
        assertEquals(1, progress.get(0).groupNumber());
        assertEquals("1a Fase", progress.get(0).phase());
        assertNull(progress.get(1).groupNumber(), "the null group sorts last");
        assertNull(progress.get(1).phase());
    }

    @Test
    void aSourceAndSeasonWithoutStoredMatchesHasNoRows() {
        storedMatch(ImportSource.BCNESA, SEASON, "altra-competicio", 1, null, 1, MatchStatus.PLAYED);

        assertTrue(matchRepository.findRoundProgress(ImportSource.RFETM, SEASON).isEmpty());
    }

    @Test
    void bothArgumentsAreRequired() {
        assertThrows(NullPointerException.class,
                () -> matchRepository.findRoundProgress(null, SEASON));
        assertThrows(NullPointerException.class,
                () -> matchRepository.findRoundProgress(ImportSource.FCTT, null));
    }

    /** FEAT-00088: the raw grouped rows behind the progress, scoped to one source and season. */
    @Test
    void findRoundStatusCountsGroupsTheScopedMatchesByRoundAndStatus() {
        storedGroupOneShape();
        storedMatch(ImportSource.RFETM, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, Season.of(2025), TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);

        List<RoundStatusCount> counts = matchRepository.findRoundStatusCounts(ImportSource.FCTT, SEASON);

        assertEquals(3, counts.size(), "round 1 played, round 1 scheduled and round 2 scheduled");
        RoundStatusCount playedOne = counts.stream()
                .filter(row -> row.round() == 1 && row.status() == MatchStatus.PLAYED)
                .findFirst().orElseThrow();
        assertEquals(TERCERA, playedOne.competition());
        assertEquals(1, playedOne.groupNumber());
        assertEquals("1a Fase", playedOne.phase());
        assertEquals(3, playedOne.matches());
        RoundStatusCount scheduledOne = counts.stream()
                .filter(row -> row.round() == 1 && row.status() == MatchStatus.SCHEDULED)
                .findFirst().orElseThrow();
        assertEquals(3, scheduledOne.matches());
        RoundStatusCount scheduledTwo = counts.stream()
                .filter(row -> row.round() == 2 && row.status() == MatchStatus.SCHEDULED)
                .findFirst().orElseThrow();
        assertEquals(6, scheduledTwo.matches());

        assertThrows(NullPointerException.class,
                () -> matchRepository.findRoundStatusCounts(null, SEASON));
        assertThrows(NullPointerException.class,
                () -> matchRepository.findRoundStatusCounts(ImportSource.FCTT, null));
    }

    // --- fixtures ----------------------------------------------------------------------------

    /** The real FCTT 2026-2027 male/tercera-nacional/G1 snapshot: 3 played + 3 pending jornada 1. */
    private void storedGroupOneShape() {
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.SCHEDULED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.SCHEDULED);
        storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 1, MatchStatus.SCHEDULED);
        for (int pending = 0; pending < 6; pending++) {
            storedMatch(ImportSource.FCTT, SEASON, TERCERA, 1, "1a Fase", 2, MatchStatus.SCHEDULED);
        }
    }

    private Match storedMatch(ImportSource source, Season season, String competition, Integer groupNumber,
                              String phase, int round, MatchStatus status) {
        Team home = storedTeam(source, season, "HOME " + UUID.randomUUID());
        Team away = storedTeam(source, season, "AWAY " + UUID.randomUUID());
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition(competition)
                .season(season)
                .groupNumber(groupNumber)
                .round(round)
                .phase(phase)
                .homeTeam(home)
                .awayTeam(away);
        if (status == MatchStatus.PLAYED) {
            builder.homeGamesWon(5).awayGamesWon(2).winnerTeam(home);
        }
        Match match = builder.status(status).createExisting();
        matchRepository.saveMatch(match);
        return match;
    }

    private Team storedTeam(ImportSource source, Season season, String name) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), source, name, season, club);
        teamRepository.saveTeam(team);
        return team;
    }
}
