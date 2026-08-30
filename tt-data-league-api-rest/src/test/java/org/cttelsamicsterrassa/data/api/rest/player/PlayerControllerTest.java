package org.cttelsamicsterrassa.data.api.rest.player;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.player.find.FindPlayerDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerGameReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerOpponentReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSeasonStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class PlayerControllerTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();

    @Test
    void rejectsShortSearchTermsAndUnknownSources() {
        PlayerController controller = controllerWith(mock(QueryBus.class));

        assertEquals(HttpStatus.BAD_REQUEST, controller.findPlayersByStringInName(" a ", null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.findPlayersByStringInName("Anna", "unknown").getStatusCode());
    }

    @Test
    void preservesSourceContextInSearchResults() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        UUID federatedId = UUID.randomUUID();
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of(
                new PlayerSearchReadModel(federatedId, "Anna Player", null,
                        List.of(new PlayerFederatedReadModel(federatedId, "Anna Player", null, ImportSource.RFETM))))));

        var response = controller.findPlayersByStringInName(" Anna ", "rfetm");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("RFETM", ((List<PlayerDto>) response.getBody()).getFirst().source());
    }

    @Test
    void exposesOneCanonicalResultWithAllSourceContexts() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        UUID canonicalId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of(
                new PlayerSearchReadModel(canonicalId, "Anna Canonical", canonicalId, List.of(
                        new PlayerFederatedReadModel(firstId, "Anna FCTT", "1", ImportSource.FCTT),
                        new PlayerFederatedReadModel(secondId, "Anna RFETM", "2", ImportSource.RFETM)),
                        List.of("2024-2025", "2023-2024")))));

        PlayerDto result = ((List<PlayerDto>) controller.findPlayersByStringInName("Anna", null).getBody()).getFirst();

        assertEquals(canonicalId, result.id());
        assertEquals(List.of("FCTT", "RFETM"), result.sources());
        assertEquals(List.of("2024-2025", "2023-2024"), result.seasons());
        assertEquals(2, result.federatedPlayers().size());
    }

    @Test
    void mapsCanonicalDetailsAndMissingPlayers() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        PlayerDetailsReadModel details = new PlayerDetailsReadModel(
                PLAYER_ID, "Anna Canonical",
                List.of(new PlayerFederatedReadModel(UUID.randomUUID(), "Anna RFETM", "123", ImportSource.RFETM)),
                List.of(), List.of(), List.of(), List.of(new PlayerMatchReadModel(UUID.randomUUID(), ImportSource.RFETM,
                        "Preferent", org.cttelsamicsterrassa.data.core.domain.shared.model.Season.of(2025), 1,
                        null, "Club Terrassa", "Club Barcelona", 4, 3, "draw", 4, "Club Terrassa")),
                List.of(new PlayerSeasonStatisticsReadModel(ImportSource.RFETM,
                        org.cttelsamicsterrassa.data.core.domain.shared.model.Season.of(2025),
                        4, 3, 1, 75.0, 2.5)));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        var response = controller.findPlayerDetailsById(PLAYER_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Anna Canonical", ((PlayerDetailsDto) response.getBody()).name());
        assertEquals("RFETM", ((PlayerDetailsDto) response.getBody()).federatedPlayers().getFirst().source());
        assertEquals(75.0, ((PlayerDetailsDto) response.getBody()).statistics().getFirst().winPercentage());
        assertEquals("Club Terrassa", ((PlayerDetailsDto) response.getBody()).matches().getFirst().playerTeam());

        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));
        assertEquals(HttpStatus.NOT_FOUND, controller.findPlayerDetailsById(PLAYER_ID).getStatusCode());
    }

    @Test
    void forwardsValidPlayerDetailFiltersToTheQuery() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        PlayerDetailsReadModel emptyDetails = new PlayerDetailsReadModel(
                PLAYER_ID, "Anna Canonical", List.of(), List.of(), List.of(),
                List.of(new PlayerCompetitionReadModel("Preferent", ImportSource.RFETM, Season.of(2024), 0)),
                List.of(), List.of());
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(emptyDetails));

        var response = controller.findPlayerDetailsById(
                PLAYER_ID, "rfetm", "2024-2025", " Preferent ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var queryCaptor = forClass(FindPlayerDetailsQuery.class);
        verify(queryBus).push(queryCaptor.capture());
        FindPlayerDetailsQuery query = queryCaptor.getValue();
        assertEquals(ImportSource.RFETM, query.getSource());
        assertEquals(Season.of(2024), query.getSeason());
        assertEquals("Preferent", query.getCompetition());
        assertEquals(0, ((PlayerDetailsDto) response.getBody()).matches().size());
    }

    @Test
    void rejectsMalformedPlayerDetailFilters() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.findPlayerDetailsById(PLAYER_ID, "unknown", null, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.findPlayerDetailsById(PLAYER_ID, null, "2024", null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.findPlayerDetailsById(PLAYER_ID, null, null, " ").getStatusCode());
        verify(queryBus, never()).push(any());
    }

    @Test
    void rejectsUnknownCompetitionFilters() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        PlayerDetailsReadModel details = new PlayerDetailsReadModel(
                PLAYER_ID, "Anna Canonical", List.of(), List.of(), List.of(),
                List.of(new PlayerCompetitionReadModel("Preferent", ImportSource.RFETM, Season.of(2024), 1)),
                List.of(), List.of());
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        var response = controller.findPlayerDetailsById(PLAYER_ID, null, null, "Unknown");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void mapsOpponentGameDetailsInTheRestResponse() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        UUID opponentId = UUID.randomUUID();
        PlayerGameReadModel game = new PlayerGameReadModel(
                UUID.randomUUID(), 1, "INDIVIDUAL", "win", 3, 1,
                List.of(new PlayerOpponentReadModel(opponentId, null, null, "Opponent Player",
                        ImportSource.FCTT, Season.of(2024), true)), null);
        PlayerMatchReadModel match = new PlayerMatchReadModel(
                UUID.randomUUID(), ImportSource.FCTT, "Preferent", Season.of(2024), 1, null,
                "Club Terrassa", "Club Beta", 4, 2, "win", 4, "Club Terrassa", List.of(game));
        PlayerDetailsReadModel details = new PlayerDetailsReadModel(
                PLAYER_ID, "Anna Canonical", List.of(), List.of(), List.of(), List.of(), List.of(match), List.of());
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        PlayerDetailsDto response = (PlayerDetailsDto) controller.findPlayerDetailsById(PLAYER_ID).getBody();

        assertEquals(opponentId, response.matches().getFirst().games().getFirst().opponents().getFirst().playerId());
        assertEquals("win", response.matches().getFirst().games().getFirst().result());
    }

    private static PlayerController controllerWith(QueryBus queryBus) {
        PlayerController controller = new PlayerController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", mock(CommandBus.class));
        return controller;
    }
}
