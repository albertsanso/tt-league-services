package org.cttelsamicsterrassa.data.api.mcp.club;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.FindClubDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.FindClubsByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.FindFederatedClubByIdQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.FindFederatedClubCompetitionDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubCompetitionDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
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
 * Read-only MCP tools exposing club data through the existing {@link QueryBus} queries.
 */
@Component
public class ClubMcpTools {

    @Autowired
    private QueryBus queryBus;

    @Tool(description = "Find a federated club by its UUID")
    public ClubDto findClubById(@ToolParam(description = "Federated club UUID") UUID id) {
        DomainQueryResponse<FederatedClub> response = queryBus.push(new FindFederatedClubByIdQuery(id));
        if (!response.isSuccess()) {
            throw new IllegalStateException("Club not found: " + id);
        }
        return ClubDto.fromObject(response.getResponse());
    }

    @Tool(description = "Get club details: season registrations and competition results")
    public ClubDetailsDto findClubDetailsById(@ToolParam(description = "Club UUID") UUID id) {
        DomainQueryResponse<?> response = queryBus.push(new FindClubDetailsQuery(id));
        if (response.isSuccess() && response.getResponse() instanceof ClubDetailsReadModel details) {
            return ClubDetailsDto.fromObject(details);
        }
        if (response.isSuccess() && response.getResponse() instanceof FederatedClubDetailsReadModel details) {
            return ClubDetailsDto.fromObject(details);
        }
        throw new IllegalStateException("Club not found: " + id);
    }

    @Tool(description = "Get a club's competition details and match results for a given season and competition")
    public ClubCompetitionDetailsDto findClubCompetitionDetails(
            @ToolParam(description = "Club UUID") UUID id,
            @ToolParam(description = "Season, formatted like 2024-2025") String season,
            @ToolParam(description = "Competition name") String competition) {
        Season parsedSeason = Season.fromFormatted(season);
        if (competition == null || competition.isBlank()) {
            throw new IllegalArgumentException("Competition must not be blank");
        }
        DomainQueryResponse<FederatedClubCompetitionDetailsReadModel> response = queryBus.push(
                new FindFederatedClubCompetitionDetailsQuery(id, parsedSeason, competition));
        if (!response.isSuccess()) {
            throw new IllegalStateException("Competition not found: " + competition);
        }
        return ClubCompetitionDetailsDto.fromObject(response.getResponse());
    }

    @Tool(description = "Search clubs whose name contains the given string, optionally scoped to a source federation")
    @SuppressWarnings("unchecked")
    public List<ClubDto> findClubsByStringInName(
            @ToolParam(description = "Search string, at least 2 characters") String name,
            @ToolParam(description = "Optional source filter, e.g. RFETM", required = false) String source) {
        String normalizedSearch = name == null ? null : name.trim();
        if (normalizedSearch == null || normalizedSearch.length() < 2) {
            throw new IllegalArgumentException("Search name must contain at least 2 characters");
        }
        ImportSource importSource = parseSource(source);
        DomainQueryResponse<?> response = queryBus.push(
                new FindClubsByStringInNameQuery(normalizedSearch, importSource));
        if (!response.isSuccess()) {
            throw new IllegalStateException("Club search failed");
        }
        List<?> results = (List<?>) response.getResponse();
        if (!results.isEmpty() && results.getFirst() instanceof FederatedClub) {
            return ((List<FederatedClub>) results).stream().map(ClubDto::fromObject).toList();
        }
        return ((List<ClubSearchReadModel>) results).stream().map(ClubDto::fromObject).toList();
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
