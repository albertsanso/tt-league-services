package org.cttelsamicsterrassa.data.api.rest.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.stats.find.FindCommunityStatisticsQuery;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

@StatsOpenAPIv1Controller
public class StatsController {

    @Autowired
    private QueryBus queryBus;

    @GetMapping("/community")
    @PreAuthorize("hasAuthority('clubs:read') and hasAuthority('players:read') and hasAuthority('matches:read')")
    @Operation(summary = "Get community statistics overview",
            description = "Returns total players, clubs, matches, and the current season, across every source")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Community statistics returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing clubs:read, players:read or matches:read permission"),
            @ApiResponse(responseCode = "500", description = "Unexpected query failure")
    })
    public ResponseEntity<?> getCommunityStatistics() {
        var response = queryBus.push(new FindCommunityStatisticsQuery());
        if (!response.isSuccess() || !(response.getResponse() instanceof CommunityStatisticsReadModel result)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorMessage("Community statistics query failed"));
        }
        return ResponseEntity.ok(CommunityStatsDto.from(result));
    }

    public record ErrorMessage(String message) {
    }
}
