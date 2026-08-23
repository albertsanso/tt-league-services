package org.cttelsamicsterrassa.data.api.rest.club;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubCompetitionDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubPlayerReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubTeamReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubControllerTest {

    private static final UUID CLUB_ID = UUID.randomUUID();

    @Test
    void rejectsShortSearchTermsBeforeQuerying() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubController controller = controllerWith(queryBus, mock(CommandBus.class));

        var response = controller.findClubsByStringInName(" a ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void trimsSearchAndPreservesSourceInResults() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubController controller = controllerWith(queryBus, mock(CommandBus.class));
        Club club = Club.createExisting(CLUB_ID, ImportSource.FCTT, "Club A");
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of(club)));

        var response = controller.findClubsByStringInName(" Club ", "fctt");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(new ClubDto(CLUB_ID, "Club A", "FCTT")), response.getBody());
    }

    @Test
    void mapsTheStableDetailsPayload() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubController controller = controllerWith(queryBus, mock(CommandBus.class));
        Season season = Season.of(2023);
        ClubDetailsReadModel details = new ClubDetailsReadModel(
                CLUB_ID,
                "Club A",
                ImportSource.RFETM,
                List.of(new ClubTeamReadModel(UUID.randomUUID(), "Club A 1", ImportSource.RFETM, season)),
                List.of(new ClubCompetitionReadModel("Divisió d'Honor", season, 3, 2, 0, 1)),
                List.of(new ClubPlayerReadModel(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Player A",
                        "Player A",
                        "123",
                        ImportSource.RFETM,
                        season,
                        List.of("Divisió d'Honor"))));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        var response = controller.findClubDetailsById(CLUB_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClubDetailsDto body = (ClubDetailsDto) response.getBody();
        assertEquals("RFETM", body.source());
        assertEquals("2023-2024", body.teams().getFirst().season());
        assertEquals(2, body.competitions().getFirst().resultTotals().wins());
        assertEquals(List.of("Divisió d'Honor"), body.players().getFirst().competitions());
    }

    @Test
    void mapsMissingClubUpdatesToNotFound() {
        CommandBus commandBus = mock(CommandBus.class);
        ClubController controller = controllerWith(mock(QueryBus.class), commandBus);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.failResponse(
                "Club not found: " + CLUB_ID));

        var response = controller.modifyClubName(CLUB_ID, new ModifyClubNameRequest("New name"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void mapsCompetitionDetailsAndRejectsMalformedSeasons() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubController controller = controllerWith(queryBus, mock(CommandBus.class));
        Season season = Season.of(2023);
        ClubCompetitionDetailsReadModel details = new ClubCompetitionDetailsReadModel(
                CLUB_ID,
                "Club A",
                ImportSource.RFETM,
                "Preferent",
                season,
                List.of(new ClubMatchReadModel(
                        UUID.randomUUID(),
                        "Club A 1",
                        "Club B 1",
                        3,
                        1,
                        "win",
                        2,
                        null,
                        null,
                        null)));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        var response = controller.findClubCompetitionDetails(
                CLUB_ID, "2023-2024", "Preferent");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClubCompetitionDetailsDto body = (ClubCompetitionDetailsDto) response.getBody();
        assertEquals("Preferent", body.competition());
        assertEquals("win", body.matches().getFirst().result());

        var malformed = controller.findClubCompetitionDetails(CLUB_ID, "2023", "Preferent");
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
    }

    private static ClubController controllerWith(QueryBus queryBus, CommandBus commandBus) {
        ClubController controller = new ClubController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", commandBus);
        return controller;
    }
}
