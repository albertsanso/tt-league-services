package org.cttelsamicsterrassa.data.api.mcp.match;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.match.find.FindMatchDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.SearchMatchesQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchPage;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.PlayerLocation;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only MCP tools exposing match data through the existing {@link QueryBus} queries.
 */
@Component
public class MatchMcpTools {

    @Autowired
    private QueryBus queryBus;

    @Tool(name = "searchMatches",
            description = "Search source-scoped matches with inclusive date bounds and optional player/club filters")
    public SearchResult search(
            @ToolParam(description = "Source federation, e.g. RFETM") String source,
            @ToolParam(description = "Season, formatted like 2024-2025") String season,
            @ToolParam(description = "Optional competition name", required = false) String competition,
            @ToolParam(description = "Optional inclusive from-date (ISO-8601)", required = false) LocalDate fromDate,
            @ToolParam(description = "Optional inclusive to-date (ISO-8601)", required = false) LocalDate toDate,
            @ToolParam(description = "Optional player UUID filter", required = false) UUID playerId,
            @ToolParam(description = "Optional player location filter: HOME, AWAY or EITHER", required = false)
            String playerLocation,
            @ToolParam(description = "Optional player name filter", required = false) String playerName,
            @ToolParam(description = "Optional club name filter", required = false) String clubName,
            @ToolParam(description = "Zero-based page index", required = false) Integer page,
            @ToolParam(description = "Page size, 1-100", required = false) Integer pageSize) {
        MatchSearchCriteria criteria = new MatchSearchCriteria(
                parseSource(source), Season.fromFormatted(season.trim()), competition, fromDate, toDate, playerId,
                playerLocation == null ? null : PlayerLocation.valueOf(playerLocation.trim().toUpperCase(Locale.ROOT)),
                playerName, clubName, page == null ? 0 : page, pageSize == null ? 10 : pageSize);
        DomainQueryResponse<?> response = queryBus.push(new SearchMatchesQuery(criteria));
        if (!response.isSuccess() || !(response.getResponse() instanceof MatchSearchPage result)) {
            throw new IllegalStateException("Match search failed");
        }
        return new SearchResult(result.matches().stream().map(MatchDto::from).toList(),
                result.total(), result.page(), result.pageSize(), result.hasNext());
    }

    @Tool(description = "Get full match details: lineups, games, sets and recent form")
    public MatchDetailDto details(@ToolParam(description = "Match UUID") UUID id) {
        DomainQueryResponse<?> response = queryBus.push(new FindMatchDetailsQuery(id));
        if (response.isSuccess() && response.getResponse() instanceof MatchDetailReadModel details) {
            return MatchDetailDto.from(details);
        }
        throw new IllegalStateException("Match not found: " + id);
    }

    private static ImportSource parseSource(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source is mandatory");
        }
        return ImportSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public record SearchResult(List<MatchDto> matches, long total, int page, int pageSize, boolean hasNext) {
    }
}
