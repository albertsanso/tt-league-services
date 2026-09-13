package org.cttelsamicsterrassa.data.api.mcp.config;

import org.cttelsamicsterrassa.data.api.mcp.club.ClubMcpTools;
import org.cttelsamicsterrassa.data.api.mcp.match.MatchMcpTools;
import org.cttelsamicsterrassa.data.api.mcp.player.PlayerMcpTools;
import org.cttelsamicsterrassa.data.api.mcp.search.SearchMcpTools;
import org.cttelsamicsterrassa.data.api.mcp.stats.StatsMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers every {@code @Tool}-annotated bean in this module with the auto-configured MCP server.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    public ToolCallbackProvider leagueDataToolCallbackProvider(
            ClubMcpTools clubMcpTools,
            PlayerMcpTools playerMcpTools,
            MatchMcpTools matchMcpTools,
            StatsMcpTools statsMcpTools,
            SearchMcpTools searchMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(clubMcpTools, playerMcpTools, matchMcpTools, statsMcpTools, searchMcpTools)
                .build();
    }
}
