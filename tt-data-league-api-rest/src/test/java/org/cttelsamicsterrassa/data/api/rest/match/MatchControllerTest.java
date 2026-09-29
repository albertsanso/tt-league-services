package org.cttelsamicsterrassa.data.api.rest.match;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarRoundReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.SeasonCalendarReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.mark.ClearMatchOverdueMarkCommand;
import org.cttelsamicsterrassa.data.core.application.match.calendar.mark.MarkMatchOverdueCommand;
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
                                CalendarMatchState.AWAITING_RESULT, false, null, null)))))))));

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

    private static MatchController controllerWith(QueryBus queryBus, CommandBus commandBus,
                                                  MatchRepository matchRepository) {
        MatchController controller = new MatchController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", commandBus);
        ReflectionTestUtils.setField(controller, "matchRepository", matchRepository);
        return controller;
    }
}