package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00092: the {@code findMatchesBySourceSeasonAndCompetition} calendar read on the JPA adapter.
 * It is the only all-status read and must be scoped to one source, season and competition.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchSeasonCalendarJpaTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final String COMPETITION = "tercera-nacional-sfd";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void returnsOnlyTheRequestedSourceSeasonAndCompetitionOfBothStatusesInOrder() {
        Match round1Played1 = stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.PLAYED);
        Match round1Scheduled1 = stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.SCHEDULED);
        stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.PLAYED);
        stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.SCHEDULED);
        stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.PLAYED);
        stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.SCHEDULED);
        for (int i = 0; i < 6; i++) {
            stored(SOURCE, SEASON, COMPETITION, 2, at(9, 19), MatchStatus.SCHEDULED);
        }

        // Decoys: same competition under another source, same source in another season, another competition.
        stored(ImportSource.RFETM, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.PLAYED);
        stored(SOURCE, Season.of(2025), COMPETITION, 1, at(9, 5), MatchStatus.PLAYED);
        stored(SOURCE, SEASON, "another-competition", 1, at(9, 5), MatchStatus.PLAYED);

        List<Match> found = matchRepository.findMatchesBySourceSeasonAndCompetition(SOURCE, SEASON, COMPETITION);

        assertEquals(12, found.size());
        assertTrue(found.stream().allMatch(m -> m.getSource() == SOURCE));
        assertTrue(found.stream().allMatch(m -> SEASON.equals(m.getSeason())));
        assertTrue(found.stream().allMatch(m -> COMPETITION.equals(m.getCompetition())));
        assertTrue(found.stream().limit(6).allMatch(m -> m.getRound() == 1));
        assertTrue(found.stream().skip(6).allMatch(m -> m.getRound() == 2));
        assertEquals(3, found.stream().filter(m -> m.getStatus() == MatchStatus.PLAYED).count());
        assertEquals(9, found.stream().filter(m -> m.getStatus() == MatchStatus.SCHEDULED).count());
        assertTrue(found.stream().anyMatch(m -> m.getId().equals(round1Played1.getId())));
        assertTrue(found.stream().anyMatch(m -> m.getId().equals(round1Scheduled1.getId())));
    }

    @Test
    void emptyScopeYieldsAnEmptyList() {
        stored(SOURCE, SEASON, COMPETITION, 1, at(9, 5), MatchStatus.PLAYED);

        assertTrue(matchRepository.findMatchesBySourceSeasonAndCompetition(
                SOURCE, SEASON, "no-such-competition").isEmpty());
    }

    @Test
    void rejectsNullAndBlankCompetition() {
        assertThrows(NullPointerException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndCompetition(null, SEASON, COMPETITION));
        assertThrows(NullPointerException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndCompetition(SOURCE, null, COMPETITION));
        assertThrows(NullPointerException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndCompetition(SOURCE, SEASON, null));
        assertThrows(IllegalArgumentException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndCompetition(SOURCE, SEASON, "  "));
    }

    // --- fixtures ----------------------------------------------------------------------------

    private ZonedDateTime at(int month, int day) {
        return ZonedDateTime.of(2026, month, day, 18, 0, 0, 0, Match.COMPETITION_ZONE);
    }

    private Match stored(ImportSource source, Season season, String competition, int round,
                         ZonedDateTime dateTime, MatchStatus status) {
        Team home = storedTeam(source, season, "HOME " + UUID.randomUUID());
        Team away = storedTeam(source, season, "AWAY " + UUID.randomUUID());
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition(competition)
                .season(season)
                .groupNumber(1)
                .round(round)
                .phase("1a Fase")
                .dateTime(dateTime)
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