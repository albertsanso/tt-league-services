package org.cttelsamicsterrassa.data.api.rest.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.api.rest.club.ClubDto;
import org.cttelsamicsterrassa.data.api.rest.match.MatchDto;
import org.cttelsamicsterrassa.data.api.rest.player.PlayerDto;
import org.cttelsamicsterrassa.data.core.application.club.find.FindClubsByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.find.FindMatchesByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.FindFederatedPlayersByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@SearchOpenAPIv1Controller
public class GlobalSearchController {

    @Autowired
    private QueryBus queryBus;

    @GetMapping
    @PreAuthorize("hasAuthority('players:read') and hasAuthority('matches:read')")
    @Operation(summary = "Search players, clubs and matches by free text",
            description = "Returns players, clubs and matches whose name contains the search string, grouped by entity type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching results returned, grouped by entity type"),
            @ApiResponse(responseCode = "400", description = "Search query is invalid"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing players:read or matches:read permission")
    })
    public ResponseEntity<?> search(@RequestParam("q") String query) {
        String normalizedSearch = query == null ? null : query.trim();
        if (normalizedSearch == null || normalizedSearch.length() < 2) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Search query must contain at least 2 characters"));
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

        return ResponseEntity.ok(new GlobalSearchResponse(players, clubs, matches));
    }

    public record GlobalSearchResponse(List<PlayerDto> players, List<ClubDto> clubs, List<MatchDto> matches) {
    }

    public record ErrorMessage(String message) {
    }
}
