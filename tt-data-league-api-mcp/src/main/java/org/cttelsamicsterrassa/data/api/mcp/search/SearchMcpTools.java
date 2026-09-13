package org.cttelsamicsterrassa.data.api.mcp.search;

import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.api.mcp.club.ClubDto;
import org.cttelsamicsterrassa.data.api.mcp.match.MatchDto;
import org.cttelsamicsterrassa.data.api.mcp.player.PlayerDto;
import org.cttelsamicsterrassa.data.core.application.club.find.FindClubsByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.find.FindMatchesByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.FindFederatedPlayersByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only MCP tool exposing free-text search across players, clubs and matches, matching
 * {@code GlobalSearchController}'s behavior.
 */
@Component
public class SearchMcpTools {

    @Autowired
    private QueryBus queryBus;

    @Tool(name = "searchGlobal",
            description = "Search players, clubs and matches by free text, grouped by entity type")
    public GlobalSearchResult search(@ToolParam(description = "Search string, at least 2 characters") String query) {
        String normalizedSearch = query == null ? null : query.trim();
        if (normalizedSearch == null || normalizedSearch.length() < 2) {
            throw new IllegalArgumentException("Search query must contain at least 2 characters");
        }

        List<PlayerDto> players = List.of();
        var playersResponse = queryBus.push(new FindFederatedPlayersByStringInNameQuery(normalizedSearch));
        if (playersResponse.isSuccess() && playersResponse.getResponse() instanceof List<?> playerResults) {
            players = playerResults.stream()
                    .map(PlayerSearchReadModel.class::cast)
                    .map(PlayerDto::fromObject)
                    .toList();
        }

        List<ClubDto> clubs = List.of();
        var clubsResponse = queryBus.push(new FindClubsByStringInNameQuery(normalizedSearch));
        if (clubsResponse.isSuccess() && clubsResponse.getResponse() instanceof List<?> clubResults) {
            clubs = clubResults.stream()
                    .map(ClubSearchReadModel.class::cast)
                    .map(ClubDto::fromObject)
                    .toList();
        }

        List<MatchDto> matches = List.of();
        var matchesResponse = queryBus.push(new FindMatchesByStringInNameQuery(normalizedSearch));
        if (matchesResponse.isSuccess() && matchesResponse.getResponse() instanceof List<?> matchResults) {
            matches = matchResults.stream()
                    .map(MatchSearchReadModel.class::cast)
                    .map(MatchDto::from)
                    .toList();
        }

        return new GlobalSearchResult(players, clubs, matches);
    }

    public record GlobalSearchResult(List<PlayerDto> players, List<ClubDto> clubs, List<MatchDto> matches) {
    }
}
