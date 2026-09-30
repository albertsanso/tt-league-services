package org.cttelsamicsterrassa.data.core.application.match.calendar.range;

import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindCalendarRangeQueryHandlerTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "tercera";
    private static final String SEGUNDA = "segunda";
    private static final LocalDate WEEK_START = LocalDate.of(2026, 9, 14);
    private static final LocalDate WEEK_END = LocalDate.of(2026, 9, 21);

    private final Team teamA = team("A Team");
    private final Team teamB = team("B Team");
    private final Team teamC = team("C Team");
    private final Team teamD = team("D Team");

    @Test
    void twoCompetitionsInOneWeekAreReturnedTogetherInDateOrder() {
        Match segundaSaturday = match(SEGUNDA, 1, 5, LocalDate.of(2026, 9, 19), 18, MatchStatus.SCHEDULED,
                teamC, teamD);
        Match terceraSaturday = match(TERCERA, 1, 5, LocalDate.of(2026, 9, 19), 10, MatchStatus.SCHEDULED,
                teamA, teamB);
        Match terceraFriday = match(TERCERA, 1, 5, LocalDate.of(2026, 9, 18), 20, MatchStatus.PLAYED,
                teamB, teamA);
        Fixture fixture = fixture(List.of(segundaSaturday, terceraSaturday, terceraFriday));

        CalendarRangeReadModel model = fixture.handle(query(null, null, null), LocalDate.of(2026, 9, 19));

        assertEquals(List.of(terceraFriday.getId(), terceraSaturday.getId(), segundaSaturday.getId()),
                model.matches().stream().map(CalendarMatchReadModel::id).toList());
        assertEquals(WEEK_START, model.from());
        assertEquals(WEEK_END, model.to());
        assertEquals(7, model.overdueGraceDays());
    }

    @Test
    void theStateOfAMatchEqualsTheJornadaViewIncludingWholeSeasonPostponement() {
        // Round 2 already has a played match, so the scheduled round 1 match is POSTPONED even though
        // round 2 lies outside the requested range.
        Match postponed = match(TERCERA, 1, 1, LocalDate.of(2026, 9, 15), 18, MatchStatus.SCHEDULED, teamA, teamB);
        Fixture fixture = fixture(List.of(postponed));
        fixture.progress(new RoundProgress(SOURCE, SEASON, TERCERA, 1, "1a Fase", 2, null, 1, 1));

        CalendarRangeReadModel model = fixture.handle(query(null, null, null), LocalDate.of(2026, 9, 16));

        assertEquals(CalendarMatchState.POSTPONED, model.matches().getFirst().calendarState());
    }

    @Test
    void aManualMarkMakesTheMatchOverdueWithItsAuthor() {
        Match scheduled = match(TERCERA, 1, 1, LocalDate.of(2026, 9, 19), 18, MatchStatus.SCHEDULED, teamA, teamB);
        Fixture fixture = fixture(List.of(scheduled));
        MatchOverdueMark mark = new MatchOverdueMark(scheduled.getId(), ZonedDateTime.now(), "admin");
        when(fixture.marks.findByMatchIds(anyCollection())).thenReturn(List.of(mark));

        CalendarRangeReadModel model = fixture.handle(query(null, null, null), LocalDate.of(2026, 9, 16));

        CalendarMatchReadModel row = model.matches().getFirst();
        assertEquals(CalendarMatchState.OVERDUE, row.calendarState());
        assertTrue(row.overdueMarked());
        assertEquals("admin", row.overdueMarkedBy());
    }

    @Test
    void competitionGroupAndTeamFiltersNarrowTheMatches() {
        Match terceraG1 = match(TERCERA, 1, 5, LocalDate.of(2026, 9, 19), 10, MatchStatus.SCHEDULED, teamA, teamB);
        Match terceraG2 = match(TERCERA, 2, 5, LocalDate.of(2026, 9, 19), 11, MatchStatus.SCHEDULED, teamC, teamD);
        Match segunda = match(SEGUNDA, 1, 5, LocalDate.of(2026, 9, 19), 12, MatchStatus.SCHEDULED, teamA, teamD);
        Fixture fixture = fixture(List.of(terceraG1, terceraG2, segunda));
        LocalDate today = LocalDate.of(2026, 9, 16);

        assertEquals(List.of(terceraG1.getId(), terceraG2.getId()),
                ids(fixture.handle(query(TERCERA, null, null), today)));
        assertEquals(List.of(terceraG2.getId()), ids(fixture.handle(query(TERCERA, 2, null), today)));
        assertEquals(List.of(terceraG1.getId(), segunda.getId()),
                ids(fixture.handle(query(null, null, teamA.getId()), today)));
        assertEquals(List.of(terceraG1.getId()),
                ids(fixture.handle(query(TERCERA, null, teamB.getId()), today)));
    }

    @Test
    void facetsAreNotShrunkByTheAppliedFilters() {
        Match terceraG1 = match(TERCERA, 1, 5, LocalDate.of(2026, 9, 19), 10, MatchStatus.SCHEDULED, teamA, teamB);
        Match terceraG2 = match(TERCERA, 2, 5, LocalDate.of(2026, 9, 19), 11, MatchStatus.SCHEDULED, teamC, teamD);
        Match segunda = match(SEGUNDA, 1, 5, LocalDate.of(2026, 9, 19), 12, MatchStatus.SCHEDULED, teamA, teamD);
        Fixture fixture = fixture(List.of(terceraG1, terceraG2, segunda));
        LocalDate today = LocalDate.of(2026, 9, 16);

        var unfiltered = fixture.handle(query(TERCERA, null, null), today).facets();
        var filtered = fixture.handle(query(TERCERA, 1, teamA.getId()), today).facets();

        assertEquals(List.of(SEGUNDA, TERCERA), filtered.competitions());
        assertEquals(List.of(1, 2), filtered.groups());
        assertEquals(unfiltered, filtered);
        assertEquals(List.of("A Team", "B Team", "C Team", "D Team"),
                filtered.teams().stream().map(t -> t.name()).toList());
    }

    @Test
    void anEmptyRangeIsASuccessWithNoMatches() {
        Fixture fixture = fixture(List.of());

        CalendarRangeReadModel model = fixture.handle(query(null, null, null), LocalDate.of(2026, 9, 16));

        assertTrue(model.matches().isEmpty());
        assertTrue(model.facets().competitions().isEmpty());
    }

    @Test
    void marksAreQueriedOnceAndNothingIsWritten() {
        Match scheduled = match(TERCERA, 1, 5, LocalDate.of(2026, 9, 19), 10, MatchStatus.SCHEDULED, teamA, teamB);
        Match played = match(TERCERA, 1, 5, LocalDate.of(2026, 9, 19), 12, MatchStatus.PLAYED, teamC, teamD);
        Fixture fixture = fixture(List.of(scheduled, played));

        fixture.handle(query(null, null, null), LocalDate.of(2026, 9, 16));

        verify(fixture.marks, times(1)).findByMatchIds(List.of(scheduled.getId()));
        verify(fixture.matches, never()).saveMatch(any());
        verify(fixture.matches, never()).saveMatches(any());
    }

    @Test
    void aRepositoryFailureIsReportedAsAFailedResponse() {
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchesBySourceSeasonAndDateRange(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("boom"));
        FindCalendarRangeQueryHandler handler = new FindCalendarRangeQueryHandler(matches,
                mock(MatchOverdueMarkRepository.class), new OverdueGracePeriod(7),
                clock(LocalDate.of(2026, 9, 16)));

        assertFalse(handler.handle(query(null, null, null)).isSuccess());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private static List<UUID> ids(CalendarRangeReadModel model) {
        return model.matches().stream().map(CalendarMatchReadModel::id).toList();
    }

    private FindCalendarRangeQuery query(String competition, Integer group, UUID teamId) {
        return new FindCalendarRangeQuery(SOURCE, SEASON, WEEK_START, WEEK_END, competition, group, teamId);
    }

    private static Clock clock(LocalDate date) {
        return Clock.fixed(ZonedDateTime.of(date, LocalTime.NOON, Match.COMPETITION_ZONE).toInstant(),
                Match.COMPETITION_ZONE);
    }

    private Fixture fixture(List<Match> matches) {
        return new Fixture(matches);
    }

    private final class Fixture {
        final MatchRepository matches = mock(MatchRepository.class);
        final MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        FindCalendarRangeQueryHandler handler;

        Fixture(List<Match> rangeMatches) {
            when(matches.findMatchesBySourceSeasonAndDateRange(any(), any(), any(), any()))
                    .thenReturn(rangeMatches);
            when(matches.findRoundProgress(SOURCE, SEASON)).thenReturn(List.of());
            when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());
        }

        void progress(RoundProgress... rows) {
            when(matches.findRoundProgress(SOURCE, SEASON)).thenReturn(List.of(rows));
        }

        CalendarRangeReadModel handle(FindCalendarRangeQuery query, LocalDate today) {
            handler = new FindCalendarRangeQueryHandler(matches, marks, new OverdueGracePeriod(7), clock(today));
            return handler.handle(query).getResponse();
        }
    }

    private Team team(String name) {
        return Team.createExisting(UUID.randomUUID(), SOURCE, name, SEASON, null);
    }

    private Match match(String competition, int group, int round, LocalDate date, int hour,
                        MatchStatus status, Team home, Team away) {
        Match.MatchBuilder builder = Match.builder().id(UUID.randomUUID()).source(SOURCE)
                .competition(competition).season(SEASON).groupNumber(group).round(round).phase("1a Fase")
                .dateTime(ZonedDateTime.of(date, LocalTime.of(hour, 0), Match.COMPETITION_ZONE))
                .homeTeam(home).awayTeam(away);
        if (status == MatchStatus.PLAYED) {
            builder.winnerTeam(home).homeGamesWon(4).awayGamesWon(1);
        }
        return builder.status(status).createExisting();
    }
}
