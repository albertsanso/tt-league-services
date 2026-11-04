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

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00093: the {@code findMatchesBySourceSeasonAndDateRange} calendar read on the JPA adapter.
 * It returns every status across competitions, scoped to one source and season and to a
 * {@code [from, to)} date range.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchCalendarRangeJpaTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void returnsBothStatusesOfEveryCompetitionInTheRangeInDateOrder() {
        Match sundayB = stored(SOURCE, SEASON, "comp-b", at(9, 13, 10), MatchStatus.SCHEDULED);
        Match saturdayA = stored(SOURCE, SEASON, "comp-a", at(9, 12, 18), MatchStatus.PLAYED);
        Match saturdayEarly = stored(SOURCE, SEASON, "comp-b", at(9, 12, 10), MatchStatus.SCHEDULED);

        List<Match> found = matchRepository.findMatchesBySourceSeasonAndDateRange(
                SOURCE, SEASON, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19));

        assertEquals(List.of(saturdayEarly.getId(), saturdayA.getId(), sundayB.getId()),
                found.stream().map(Match::getId).toList());
        assertEquals(2, found.stream().map(Match::getCompetition).distinct().count());
    }

    @Test
    void fromIsIncludedAndToIsExcluded() {
        Match onFrom = stored(SOURCE, SEASON, "comp-a", at(9, 12, 18), MatchStatus.SCHEDULED);
        stored(SOURCE, SEASON, "comp-a", at(9, 19, 18), MatchStatus.SCHEDULED);

        List<Match> found = matchRepository.findMatchesBySourceSeasonAndDateRange(
                SOURCE, SEASON, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19));

        assertEquals(List.of(onFrom.getId()), found.stream().map(Match::getId).toList());
    }

    @Test
    void decoysAreExcluded() {
        Match inside = stored(SOURCE, SEASON, "comp-a", at(9, 12, 18), MatchStatus.SCHEDULED);
        stored(ImportSource.RFETM, SEASON, "comp-a", at(9, 12, 18), MatchStatus.SCHEDULED);
        stored(SOURCE, Season.of(2025), "comp-a", at(9, 12, 18), MatchStatus.SCHEDULED);
        stored(SOURCE, SEASON, "comp-a", at(10, 3, 18), MatchStatus.SCHEDULED);
        stored(SOURCE, SEASON, "comp-a", null, MatchStatus.SCHEDULED);

        List<Match> found = matchRepository.findMatchesBySourceSeasonAndDateRange(
                SOURCE, SEASON, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertEquals(List.of(inside.getId()), found.stream().map(Match::getId).toList());
    }

    @Test
    void rejectsNullArgumentsAndAnEmptyOrReversedRange() {
        LocalDate from = LocalDate.of(2026, 9, 12);
        LocalDate to = LocalDate.of(2026, 9, 19);
        assertThrows(NullPointerException.class,
                () -> matchRepository.findMatchesBySourceSeasonAndDateRange(null, SEASON, from, to));
        assertThrows(NullPointerException.class,
                () -> matchRepository.findMatchesBySourceSeasonAndDateRange(SOURCE, null, from, to));
        assertThrows(NullPointerException.class,
                () -> matchRepository.findMatchesBySourceSeasonAndDateRange(SOURCE, SEASON, null, to));
        assertThrows(NullPointerException.class,
                () -> matchRepository.findMatchesBySourceSeasonAndDateRange(SOURCE, SEASON, from, null));
        assertThrows(IllegalArgumentException.class,
                () -> matchRepository.findMatchesBySourceSeasonAndDateRange(SOURCE, SEASON, from, from));
        assertThrows(IllegalArgumentException.class,
                () -> matchRepository.findMatchesBySourceSeasonAndDateRange(SOURCE, SEASON, to, from));
    }

    @Test
    void anEmptyRangeYieldsAnEmptyList() {
        stored(SOURCE, SEASON, "comp-a", at(9, 12, 18), MatchStatus.SCHEDULED);

        assertTrue(matchRepository.findMatchesBySourceSeasonAndDateRange(
                SOURCE, SEASON, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)).isEmpty());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private ZonedDateTime at(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, Match.COMPETITION_ZONE);
    }

    private Match stored(ImportSource source, Season season, String competition, ZonedDateTime dateTime,
                         MatchStatus status) {
        Team home = storedTeam(source, season, "HOME " + UUID.randomUUID());
        Team away = storedTeam(source, season, "AWAY " + UUID.randomUUID());
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition(competition)
                .season(season)
                .groupNumber(1)
                .round(1)
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
