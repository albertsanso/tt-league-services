package org.cttelsamicsterrassa.data.api.rest.player;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer.createExisting(
                        federatedId, ImportSource.RFETM, "Anna Player"))));

        var response = controller.findPlayersByStringInName(" Anna ", "rfetm");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("RFETM", ((List<PlayerDto>) response.getBody()).getFirst().source());
    }

    @Test
    void mapsCanonicalDetailsAndMissingPlayers() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerController controller = controllerWith(queryBus);
        PlayerDetailsReadModel details = new PlayerDetailsReadModel(
                PLAYER_ID, "Anna Canonical",
                List.of(new PlayerFederatedReadModel(UUID.randomUUID(), "Anna RFETM", "123", ImportSource.RFETM)),
                List.of(), List.of(), List.of(), List.of());
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        var response = controller.findPlayerDetailsById(PLAYER_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Anna Canonical", ((PlayerDetailsDto) response.getBody()).name());
        assertEquals("RFETM", ((PlayerDetailsDto) response.getBody()).federatedPlayers().getFirst().source());

        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));
        assertEquals(HttpStatus.NOT_FOUND, controller.findPlayerDetailsById(PLAYER_ID).getStatusCode());
    }

    private static PlayerController controllerWith(QueryBus queryBus) {
        PlayerController controller = new PlayerController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", mock(CommandBus.class));
        return controller;
    }
}
