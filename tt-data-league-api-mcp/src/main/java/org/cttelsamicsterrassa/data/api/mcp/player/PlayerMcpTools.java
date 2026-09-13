package org.cttelsamicsterrassa.data.api.mcp.player;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.player.find.FindFederatedPlayersByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.FindPlayerDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only MCP tools exposing player data through the existing {@link QueryBus} queries.
 */
@Component
public class PlayerMcpTools {

    @Autowired
    private QueryBus queryBus;

    @Tool(description = "Search players whose name contains the given string, optionally scoped to a source federation")
    public List<PlayerDto> findPlayersByStringInName(
            @ToolParam(description = "Search string, at least 2 characters") String name,
            @ToolParam(description = "Optional source filter, e.g. RFETM", required = false) String source) {
        String normalizedSearch = name == null ? null : name.trim();
        if (normalizedSearch == null || normalizedSearch.length() < 2) {
            throw new IllegalArgumentException("Search name must contain at least 2 characters");
        }
        ImportSource importSource = parseSource(source);
        DomainQueryResponse<?> response = queryBus.push(
                new FindFederatedPlayersByStringInNameQuery(normalizedSearch, importSource));
        if (!response.isSuccess()) {
            throw new IllegalStateException("Player search failed");
        }
        return ((List<?>) response.getResponse()).stream()
                .map(PlayerSearchReadModel.class::cast)
                .map(PlayerDto::fromObject)
                .toList();
    }

    @Tool(description = "Get canonical player details: registrations, clubs, competitions and matches, "
            + "optionally scoped to a source, season and competition")
    public PlayerDetailsDto findPlayerDetailsById(
            @ToolParam(description = "Canonical player UUID") UUID id,
            @ToolParam(description = "Optional source filter, e.g. RFETM", required = false) String source,
            @ToolParam(description = "Optional season filter, formatted like 2024-2025", required = false) String season,
            @ToolParam(description = "Optional competition filter", required = false) String competition) {
        ImportSource parsedSource = parseSource(source);
        Season parsedSeason = parseSeason(season);
        if (competition != null && competition.isBlank()) {
            throw new IllegalArgumentException("Competition must not be blank");
        }
        String parsedCompetition = competition == null ? null : competition.trim();
        DomainQueryResponse<?> response = queryBus.push(
                new FindPlayerDetailsQuery(id, parsedSource, parsedSeason, parsedCompetition));
        if (!response.isSuccess() || !(response.getResponse() instanceof PlayerDetailsReadModel details)) {
            throw new IllegalStateException("Player not found: " + id);
        }
        if (parsedCompetition != null && details.competitions().stream()
                .noneMatch(item -> parsedCompetition.equals(item.name()))) {
            throw new IllegalArgumentException("Unknown competition filter: " + parsedCompetition);
        }
        return PlayerDetailsDto.fromObject(details);
    }

    private static Season parseSeason(String season) {
        if (season == null) {
            return null;
        }
        if (season.isBlank()) {
            throw new IllegalArgumentException("season must not be blank");
        }
        return Season.fromFormatted(season.trim());
    }

    private static ImportSource parseSource(String source) {
        if (source == null) {
            return null;
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        return ImportSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
