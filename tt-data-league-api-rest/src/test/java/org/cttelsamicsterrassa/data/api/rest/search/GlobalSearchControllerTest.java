package org.cttelsamicsterrassa.data.api.rest.search;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.FindClubsByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.find.FindMatchesByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.FindFederatedPlayersByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalSearchControllerTest {

    @Test
    void rejectsSearchTermsShorterThanTwoCharacters() {
        GlobalSearchController controller = controllerWith(mock(QueryBus.class));

        assertEquals(HttpStatus.BAD_REQUEST, controller.search(" a ").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.search(null).getStatusCode());
    }

    @Test
    void returnsResultsGroupedByEntityType() {
        QueryBus queryBus = mock(QueryBus.class);
        GlobalSearchController controller = controllerWith(queryBus);
        UUID playerId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        when(queryBus.push(any(FindFederatedPlayersByStringInNameQuery.class)))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of(
                        new PlayerSearchReadModel(playerId, "Anna Player", playerId, List.of(
                                new PlayerFederatedReadModel(playerId, "Anna Player", null, ImportSource.RFETM))))));
        when(queryBus.push(any(FindClubsByStringInNameQuery.class)))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of(
                        new ClubSearchReadModel(clubId, "Anna Club", List.of(), List.of(), 0, List.of()))));
        when(queryBus.push(any(FindMatchesByStringInNameQuery.class)))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of(
                        new MatchSearchReadModel(matchId, ImportSource.RFETM, "Primera", null, 1, "REGULAR",
                                null, "Anna's Club", "Other Club", null, null, null, null, null,
                                false, List.of(), List.of()))));

        var response = controller.search(" Anna ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalSearchController.GlobalSearchResponse body =
                (GlobalSearchController.GlobalSearchResponse) response.getBody();
        assertEquals(1, body.players().size());
        assertEquals(playerId, body.players().getFirst().id());
        assertEquals(1, body.clubs().size());
        assertEquals(clubId, body.clubs().getFirst().id());
        assertEquals(1, body.matches().size());
        assertEquals(matchId, body.matches().getFirst().id());
    }

    @Test
    void degradesToAnEmptyGroupWhenOneQueryFails() {
        QueryBus queryBus = mock(QueryBus.class);
        GlobalSearchController controller = controllerWith(queryBus);

        when(queryBus.push(any(FindFederatedPlayersByStringInNameQuery.class)))
                .thenReturn(DomainQueryResponse.failResponse(List.of()));
        when(queryBus.push(any(FindClubsByStringInNameQuery.class)))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of()));
        when(queryBus.push(any(FindMatchesByStringInNameQuery.class)))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of()));

        var response = controller.search("Anna");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalSearchController.GlobalSearchResponse body =
                (GlobalSearchController.GlobalSearchResponse) response.getBody();
        assertTrue(body.players().isEmpty());
        assertTrue(body.clubs().isEmpty());
        assertTrue(body.matches().isEmpty());
    }

    private static GlobalSearchController controllerWith(QueryBus queryBus) {
        GlobalSearchController controller = new GlobalSearchController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        return controller;
    }
}
