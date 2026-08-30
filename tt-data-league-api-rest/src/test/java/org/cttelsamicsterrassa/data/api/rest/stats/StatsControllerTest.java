package org.cttelsamicsterrassa.data.api.rest.stats;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.stats.find.FindCommunityStatisticsQuery;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.SeasonAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsControllerTest {

    @Test
    void returnsCommunityStatisticsFromTheQueryResponse() {
        QueryBus queryBus = mock(QueryBus.class);
        StatsController controller = controllerWith(queryBus);
        CommunityStatisticsReadModel model = new CommunityStatisticsReadModel(
                new CommunityStatisticsReadModel.CountSummary(1248),
                new CommunityStatisticsReadModel.CountSummary(186),
                new CommunityStatisticsReadModel.CountSummary(8432),
                new CommunityStatisticsReadModel.CurrentSeasonSummary("2025-2026", SeasonAvailability.IN_PROGRESS));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(model));

        var response = controller.getCommunityStatistics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CommunityStatsDto body = (CommunityStatsDto) response.getBody();
        assertEquals(1248, body.players().total());
        assertEquals(186, body.clubs().total());
        assertEquals(8432, body.matches().total());
        assertEquals("2025-2026", body.season().name());
        assertEquals("IN_PROGRESS", body.season().status());
        verify(queryBus).push(any(FindCommunityStatisticsQuery.class));
    }

    @Test
    void returnsUnavailableSeasonWhenNoMatchDataExists() {
        QueryBus queryBus = mock(QueryBus.class);
        StatsController controller = controllerWith(queryBus);
        CommunityStatisticsReadModel model = new CommunityStatisticsReadModel(
                new CommunityStatisticsReadModel.CountSummary(0),
                new CommunityStatisticsReadModel.CountSummary(0),
                new CommunityStatisticsReadModel.CountSummary(0),
                new CommunityStatisticsReadModel.CurrentSeasonSummary(null, SeasonAvailability.UNAVAILABLE));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(model));

        var response = controller.getCommunityStatistics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CommunityStatsDto body = (CommunityStatsDto) response.getBody();
        assertEquals(0, body.players().total());
        assertEquals(null, body.season().name());
        assertEquals("UNAVAILABLE", body.season().status());
    }

    @Test
    void returnsServerErrorWhenTheQueryFails() {
        QueryBus queryBus = mock(QueryBus.class);
        StatsController controller = controllerWith(queryBus);
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        var response = controller.getCommunityStatistics();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    private static StatsController controllerWith(QueryBus queryBus) {
        StatsController controller = new StatsController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        return controller;
    }
}
