package org.cttelsamicsterrassa.data.api.rest.match;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarRoundReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.SeasonCalendarReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeFacets;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarTeamFacet;
import org.cttelsamicsterrassa.data.core.application.match.calendar.mark.ClearMatchOverdueMarkCommand;
import org.cttelsamicsterrassa.data.core.application.match.calendar.mark.MarkMatchOverdueCommand;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.FindRoundProgressQuery;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.JornadaProgressReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchControllerTest {

    @Test
    void mapsACalendarResponseWithStringStatesAndEmptyMarkFields() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        UUID matchId = UUID.randomUUID();
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(
                new SeasonCalendarReadModel(ImportSource.FCTT, Season.of(2026), "tercera",
                        LocalDate.of(2026, 9, 8), 7, List.of(new CalendarGroupReadModel(1, "1a Fase", 1, null,
                        9, 3, 0, 0, List.of(new CalendarRoundReadModel(1, LocalDate.of(2026, 9, 5),
                        LocalDate.of(2026, 9, 5), 3, 3, false, true,
                        List.of(new CalendarMatchReadModel(matchId, ZonedDateTime.now(), "city", "venue",
                                "Home", "Away", null, null, null, MatchStatus.SCHEDULED,
                                CalendarMatchState.AWAITING_RESULT, false, null, null,
                                "tercera", 1, "1a Fase", 1, UUID.randomUUID(), UUID.randomUUID(),
                                true)))))))));

        var response = controller.calendar("FCTT", "2026-2027", "tercera", null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SeasonCalendarDto body = (SeasonCalendarDto) response.getBody();
        assertEquals("FCTT", body.source());
        assertEquals("2026-2027", body.season());
        assertEquals("tercera", body.competition());
        SeasonCalendarDto.CalendarMatchDto match = body.groups().getFirst().rounds().getFirst().matches().getFirst();
        assertEquals("SCHEDULED", match.status());
        assertEquals("AWAITING_RESULT", match.calendarState());
        assertFalse(match.overdueMarked());
        assertNull(match.overdueMarkedAt());
        assertNull(match.overdueMarkedBy());
        assertEquals("tercera", match.competition());
        assertEquals(1, match.round());
        assertNotNull(match.homeTeamId());
    }

    @Test
    void aCalendarWithNoMatchesHasEmptyGroups() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(
                new SeasonCalendarReadModel(ImportSource.FCTT, Season.of(2026), "tercera",
                        LocalDate.of(2026, 9, 8), 7, List.of())));

        var response = controller.calendar("FCTT", "2026-2027", "tercera", null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(((SeasonCalendarDto) response.getBody()).groups().isEmpty());
    }

    @Test
    void aMissingCompetitionOrInvalidSourceIsRejected() {
        MatchController controller = controllerWith(mock(QueryBus.class), mock(CommandBus.class),
                mock(MatchRepository.class));

        assertEquals(HttpStatus.BAD_REQUEST, controller.calendar("FCTT", "2026-2027", "  ", null, null)
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.calendar("NOPE", "2026-2027", "tercera", null, null)
                .getStatusCode());
    }

    @Test
    void aHandlerFailureIsAServerError() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.calendar("FCTT", "2026-2027", "tercera", null, null).getStatusCode());
    }

    @Test
    void mapsACalendarRangeResponseWithFacetsAndTeamIds() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(
                new CalendarRangeReadModel(ImportSource.FCTT, Season.of(2026), LocalDate.of(2026, 9, 14),
                        LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 16), 7,
                        List.of(new CalendarMatchReadModel(UUID.randomUUID(), ZonedDateTime.now(), "city",
                                "venue", "Home", "Away", null, null, null, MatchStatus.SCHEDULED,
                                CalendarMatchState.UPCOMING, false, null, null, "tercera", 1, "1a Fase", 2,
                                home, away, false)),
                        new CalendarRangeFacets(List.of("tercera"), List.of(1),
                                List.of(new CalendarTeamFacet(home, "Home", "tercera"))))));

        var response = controller.calendarRange("FCTT", "2026-2027", "2026-09-14", "2026-09-21",
                "tercera", "1", home.toString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CalendarRangeDto body = (CalendarRangeDto) response.getBody();
        assertEquals("FCTT", body.source());
        assertEquals("2026-2027", body.season());
        assertEquals(LocalDate.of(2026, 9, 21), body.to());
        assertEquals("UPCOMING", body.matches().getFirst().calendarState());
        assertEquals(home, body.matches().getFirst().homeTeamId());
        assertEquals(List.of("tercera"), body.facets().competitions());
        assertEquals("Home", body.facets().teams().getFirst().name());
    }

    @Test
    void aCalendarRangeAcceptsMissingOptionalParameters() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(
                new CalendarRangeReadModel(ImportSource.FCTT, Season.of(2026), LocalDate.of(2026, 9, 14),
                        LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 16), 7, List.of(),
                        new CalendarRangeFacets(List.of(), List.of(), List.of()))));

        var response = controller.calendarRange("FCTT", "2026-2027", "2026-09-14", "2026-09-21",
                null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(((CalendarRangeDto) response.getBody()).matches().isEmpty());
    }

    @Test
    void anInvalidCalendarRangeIsRejectedWith400() {
        MatchController controller = controllerWith(mock(QueryBus.class), mock(CommandBus.class),
                mock(MatchRepository.class));

        assertBadRange(controller.calendarRange("FCTT", "2026-2027", "nope", "2026-09-21", null, null, null));
        assertBadRange(controller.calendarRange("FCTT", "2026-2027", "2026-09-21", "2026-09-14", null, null, null));
        assertBadRange(controller.calendarRange("FCTT", "2026-2027", "2026-09-21", "2026-09-21", null, null, null));
        assertBadRange(controller.calendarRange("FCTT", "2026-2027", "2026-09-01", "2026-12-01", null, null, null));
        assertBadRange(controller.calendarRange("FCTT", "2026-2027", "2026-09-14", "2026-09-21", null, null,
                "not-a-uuid"));
        assertBadRange(controller.calendarRange("NOPE", "2026-2027", "2026-09-14", "2026-09-21", null, null, null));
        assertBadRange(controller.calendarRange("FCTT", "2026-2027", "2026-09-14", "2026-09-21", null, "1", null));
    }

    @Test
    void aCalendarRangeHandlerFailureIsAServerError() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        var response = controller.calendarRange("FCTT", "2026-2027", "2026-09-14", "2026-09-21", null, null, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Calendar range failed", ((MatchController.ErrorMessage) response.getBody()).message());
    }

    private static void assertBadRange(org.springframework.http.ResponseEntity<?> response) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid calendar range", ((MatchController.ErrorMessage) response.getBody()).message());
    }

    @Test
    void markingOverduePassesTheAuthenticatedUserNameAndReturnsNoContent() {
        CommandBus commandBus = mock(CommandBus.class);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse(true));
        MatchController controller = controllerWith(mock(QueryBus.class), commandBus, mock(MatchRepository.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        UUID id = UUID.randomUUID();

        var response = controller.markOverdue(id, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        ArgumentCaptor<MarkMatchOverdueCommand> captor = ArgumentCaptor.forClass(MarkMatchOverdueCommand.class);
        verify(commandBus).push(captor.capture());
        assertEquals(id, captor.getValue().getMatchId());
        assertEquals("admin", captor.getValue().getMarkedBy());
    }

    @Test
    void markingAnUnknownMatchIs404AndAPlayedMatchIs409() {
        CommandBus commandBus = mock(CommandBus.class);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.failResponse("Match not found: x"));
        MatchController controller = controllerWith(mock(QueryBus.class), commandBus, mock(MatchRepository.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");

        assertEquals(HttpStatus.NOT_FOUND, controller.markOverdue(UUID.randomUUID(), authentication)
                .getStatusCode());

        when(commandBus.push(any())).thenReturn(DomainCommandResponse.failResponse(
                "Only scheduled matches can be marked overdue"));
        assertEquals(HttpStatus.CONFLICT, controller.markOverdue(UUID.randomUUID(), authentication)
                .getStatusCode());

        String tooEarly = "A match can only be marked overdue from the day after its scheduled date";
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.failResponse(tooEarly));
        var tooEarlyResponse = controller.markOverdue(UUID.randomUUID(), authentication);
        assertEquals(HttpStatus.CONFLICT, tooEarlyResponse.getStatusCode());
        assertEquals(new MatchController.ErrorMessage(tooEarly), tooEarlyResponse.getBody());
    }

    @Test
    void clearingReturnsNoContentAndAnUnknownMatchIs404() {
        CommandBus commandBus = mock(CommandBus.class);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse(true));
        MatchController controller = controllerWith(mock(QueryBus.class), commandBus, mock(MatchRepository.class));
        UUID id = UUID.randomUUID();

        assertEquals(HttpStatus.NO_CONTENT, controller.clearOverdueMark(id).getStatusCode());
        verify(commandBus).push(any(ClearMatchOverdueMarkCommand.class));

        when(commandBus.push(any())).thenReturn(DomainCommandResponse.failResponse("Match not found: x"));
        assertEquals(HttpStatus.NOT_FOUND, controller.clearOverdueMark(id).getStatusCode());
    }

    @Test
    void mapsARoundProgressResponseAndPassesTheFiltersToTheQuery() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(
                new RoundProgressReadModel(ImportSource.FCTT, Season.of(2026), "TERCERA", true,
                        LocalDate.of(2026, 10, 4), 7, List.of(new RoundProgressGroupReadModel("TERCERA", 2,
                        "1a Fase", 3, 2, List.of(new JornadaProgressReadModel(3, LocalDate.of(2026, 9, 26),
                        LocalDate.of(2026, 9, 27), 2, 4, 0, 1, 1, 0, false, true, true)))))));

        var response = controller.roundProgress("FCTT", "2026-2027", " TERCERA ", "TRUE");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoundProgressDto body = (RoundProgressDto) response.getBody();
        assertEquals("FCTT", body.source());
        assertEquals("2026-2027", body.season());
        assertTrue(body.onlyOpen());
        assertEquals(7, body.overdueGraceDays());
        RoundProgressDto.JornadaProgressDto round = body.groups().getFirst().rounds().getFirst();
        assertEquals(3, round.round());
        assertEquals(1, round.overdueMatches());
        assertEquals(1, round.awaitingResultMatches());
        assertTrue(round.open());
        ArgumentCaptor<FindRoundProgressQuery> captor = ArgumentCaptor.forClass(FindRoundProgressQuery.class);
        verify(queryBus).push(captor.capture());
        assertEquals("TERCERA", captor.getValue().getCompetition());
        assertTrue(captor.getValue().isOnlyOpen());
    }

    @Test
    void roundProgressDefaultsOnlyOpenToFalseAndNoCompetition() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(
                new RoundProgressReadModel(ImportSource.FCTT, Season.of(2026), null, false,
                        LocalDate.of(2026, 10, 4), 7, List.of())));

        assertEquals(HttpStatus.OK, controller.roundProgress("FCTT", "2026-2027", null, null).getStatusCode());

        ArgumentCaptor<FindRoundProgressQuery> captor = ArgumentCaptor.forClass(FindRoundProgressQuery.class);
        verify(queryBus).push(captor.capture());
        assertNull(captor.getValue().getCompetition());
        assertFalse(captor.getValue().isOnlyOpen());
    }

    @Test
    void invalidRoundProgressFiltersAreRejected() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.roundProgress("FCTT", "2026-2027", null, "yes").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.roundProgress("FCTT", "2026-2027", "  ", null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.roundProgress("NOPE", "2026-2027", null, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.roundProgress("FCTT", "bad", null, null).getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(queryBus);
    }

    @Test
    void aRoundProgressHandlerFailureIsAServerError() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchController controller = controllerWith(queryBus, mock(CommandBus.class), mock(MatchRepository.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.roundProgress("FCTT", "2026-2027", null, null).getStatusCode());
    }

    private static MatchController controllerWith(QueryBus queryBus, CommandBus commandBus,
                                                  MatchRepository matchRepository) {
        MatchController controller = new MatchController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", commandBus);
        ReflectionTestUtils.setField(controller, "matchRepository", matchRepository);
        return controller;
    }
}