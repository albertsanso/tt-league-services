package org.cttelsamicsterrassa.data.core.application.match.calendar;

import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarMatchAssemblerTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final LocalDate DATE = LocalDate.of(2026, 9, 5);

    @Test
    void marksAreLookedUpOnceWithScheduledIdsOnly() {
        Match played = match(MatchStatus.PLAYED);
        Match scheduled = match(MatchStatus.SCHEDULED);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());

        new CalendarMatchAssembler(marks, new OverdueGracePeriod(7)).marksFor(List.of(played, scheduled));

        verify(marks, times(1)).findByMatchIds(List.of(scheduled.getId()));
    }

    @Test
    void aMarkIsIgnoredForAPlayedMatchAndExposedForAScheduledOne() {
        Match played = match(MatchStatus.PLAYED);
        Match scheduled = match(MatchStatus.SCHEDULED);
        ZonedDateTime markedAt = ZonedDateTime.now();
        Map<UUID, MatchOverdueMark> marks = Map.of(
                played.getId(), new MatchOverdueMark(played.getId(), markedAt, "admin"),
                scheduled.getId(), new MatchOverdueMark(scheduled.getId(), markedAt, "admin"));
        CalendarMatchAssembler assembler = new CalendarMatchAssembler(
                mock(MatchOverdueMarkRepository.class), new OverdueGracePeriod(7));

        CalendarMatchReadModel playedModel = assembler.toReadModel(played,
                assembler.state(played, 1, marks, DATE), marks, DATE);
        CalendarMatchReadModel scheduledModel = assembler.toReadModel(scheduled,
                assembler.state(scheduled, 1, marks, DATE), marks, DATE);

        assertFalse(playedModel.overdueMarked());
        assertNull(playedModel.overdueMarkedBy());
        assertEquals(CalendarMatchState.PLAYED, playedModel.calendarState());
        assertTrue(scheduledModel.overdueMarked());
        assertEquals("admin", scheduledModel.overdueMarkedBy());
        assertEquals(CalendarMatchState.OVERDUE, scheduledModel.calendarState());
    }

    @Test
    void theReadModelCarriesCompetitionGroupPhaseRoundAndTeamIds() {
        Match scheduled = match(MatchStatus.SCHEDULED);
        CalendarMatchAssembler assembler = new CalendarMatchAssembler(
                mock(MatchOverdueMarkRepository.class), new OverdueGracePeriod(7));

        CalendarMatchReadModel model = assembler.toReadModel(scheduled, CalendarMatchState.UPCOMING, Map.of(), DATE);

        assertEquals("tercera", model.competition());
        assertEquals(1, model.groupNumber());
        assertEquals("1a Fase", model.phase());
        assertEquals(3, model.round());
        assertEquals(scheduled.getHomeTeam().getId(), model.homeTeamId());
        assertEquals(scheduled.getAwayTeam().getId(), model.awayTeamId());
    }

    @Test
    void overdueMarkableOpensTheDayAfterTheMatchDate() {
        Match scheduled = match(MatchStatus.SCHEDULED);
        Match played = match(MatchStatus.PLAYED);
        CalendarMatchAssembler assembler = new CalendarMatchAssembler(
                mock(MatchOverdueMarkRepository.class), new OverdueGracePeriod(7));

        assertFalse(assembler.toReadModel(scheduled, CalendarMatchState.UPCOMING, Map.of(), DATE)
                .overdueMarkable());
        assertTrue(assembler.toReadModel(scheduled, CalendarMatchState.AWAITING_RESULT, Map.of(),
                DATE.plusDays(1)).overdueMarkable());
        assertFalse(assembler.toReadModel(played, CalendarMatchState.PLAYED, Map.of(), DATE.plusDays(1))
                .overdueMarkable());
    }

    private Match match(MatchStatus status) {
        Team home = Team.createExisting(UUID.randomUUID(), SOURCE, "Home", SEASON, null);
        Team away = Team.createExisting(UUID.randomUUID(), SOURCE, "Away", SEASON, null);
        Match.MatchBuilder builder = Match.builder().id(UUID.randomUUID()).source(SOURCE)
                .competition("tercera").season(SEASON).groupNumber(1).round(3).phase("1a Fase")
                .dateTime(ZonedDateTime.of(DATE, java.time.LocalTime.of(18, 30), Match.COMPETITION_ZONE))
                .homeTeam(home).awayTeam(away);
        if (status == MatchStatus.PLAYED) {
            builder.winnerTeam(home).homeGamesWon(4).awayGamesWon(1);
        }
        return builder.status(status).createExisting();
    }
}
