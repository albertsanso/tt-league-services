package org.cttelsamicsterrassa.data.api.mcp.stats;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.stats.find.FindCommunityStatisticsQuery;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read-only MCP tools exposing community-wide statistics through the existing {@link QueryBus} query.
 */
@Component
public class StatsMcpTools {

    @Autowired
    private QueryBus queryBus;

    @Tool(description = "Get community statistics overview: total players, clubs, matches, and the current season, "
            + "across every source")
    public CommunityStatsDto getCommunityStatistics() {
        DomainQueryResponse<?> response = queryBus.push(new FindCommunityStatisticsQuery());
        if (!response.isSuccess() || !(response.getResponse() instanceof CommunityStatisticsReadModel result)) {
            throw new IllegalStateException("Community statistics query failed");
        }
        return CommunityStatsDto.from(result);
    }
}
