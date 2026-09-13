package org.cttelsamicsterrassa.data.api.mcp.player;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerMcpToolsTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();

    @Test
    void findPlayersByStringInNameRejectsShortSearchTerms() {
        PlayerMcpTools tools = toolsWith(mock(QueryBus.class));

        assertThrows(IllegalArgumentException.class, () -> tools.findPlayersByStringInName(" a ", null));
    }

    @Test
    void findPlayersByStringInNameMapsSearchReadModels() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerMcpTools tools = toolsWith(queryBus);
        UUID federatedId = UUID.randomUUID();
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of(
                new PlayerSearchReadModel(
                        PLAYER_ID,
                        "Player A",
                        PLAYER_ID,
                        List.of(new PlayerFederatedReadModel(federatedId, "Player A", "L123", ImportSource.RFETM))))));

        List<PlayerDto> results = tools.findPlayersByStringInName("Player A", null);

        assertEquals(PLAYER_ID, results.getFirst().id());
        assertEquals(List.of("RFETM"), results.getFirst().sources());
    }

    @Test
    void findPlayerDetailsByIdThrowsWhenNotFound() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerMcpTools tools = toolsWith(queryBus);
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        assertThrows(IllegalStateException.class, () -> tools.findPlayerDetailsById(PLAYER_ID, null, null, null));
    }

    @Test
    void findPlayerDetailsByIdMapsDetails() {
        QueryBus queryBus = mock(QueryBus.class);
        PlayerMcpTools tools = toolsWith(queryBus);
        PlayerDetailsReadModel details = new PlayerDetailsReadModel(
                PLAYER_ID, "Player A", List.of(), List.of(), List.of(), List.of(), List.of());
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(details));

        PlayerDetailsDto result = tools.findPlayerDetailsById(PLAYER_ID, null, null, null);

        assertEquals(PLAYER_ID, result.id());
        assertEquals("Player A", result.name());
    }

    private static PlayerMcpTools toolsWith(QueryBus queryBus) {
        PlayerMcpTools tools = new PlayerMcpTools();
        ReflectionTestUtils.setField(tools, "queryBus", queryBus);
        return tools;
    }
}
