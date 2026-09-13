package org.cttelsamicsterrassa.data.api.mcp.stats;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.SeasonAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsMcpToolsTest {

    @Test
    void getCommunityStatisticsMapsReadModel() {
        QueryBus queryBus = mock(QueryBus.class);
        StatsMcpTools tools = toolsWith(queryBus);
        CommunityStatisticsReadModel readModel = new CommunityStatisticsReadModel(
                new CommunityStatisticsReadModel.CountSummary(10),
                new CommunityStatisticsReadModel.CountSummary(3),
                new CommunityStatisticsReadModel.CountSummary(42),
                new CommunityStatisticsReadModel.CurrentSeasonSummary("2024-2025", SeasonAvailability.IN_PROGRESS));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(readModel));

        CommunityStatsDto result = tools.getCommunityStatistics();

        assertEquals(10, result.players().total());
        assertEquals(42, result.matches().total());
        assertEquals("2024-2025", result.season().name());
    }

    @Test
    void getCommunityStatisticsThrowsWhenQueryFails() {
        QueryBus queryBus = mock(QueryBus.class);
        StatsMcpTools tools = toolsWith(queryBus);
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        assertThrows(IllegalStateException.class, tools::getCommunityStatistics);
    }

    private static StatsMcpTools toolsWith(QueryBus queryBus) {
        StatsMcpTools tools = new StatsMcpTools();
        ReflectionTestUtils.setField(tools, "queryBus", queryBus);
        return tools;
    }
}
