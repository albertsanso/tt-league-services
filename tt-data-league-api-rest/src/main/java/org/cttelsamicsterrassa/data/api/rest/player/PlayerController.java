package org.cttelsamicsterrassa.data.api.rest.player;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.player.find.FindPlayerDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.FindFederatedPlayersByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DateTimeException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@PlayerOpenAPIv1Controller
public class PlayerController {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private CommandBus commandBus;

    @GetMapping("/search_in_name")
    @PreAuthorize("hasAuthority('players:read')")
    @Operation(summary = "Search players by string in name", description = "Returns a list of players whose names contain the specified search string")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching players returned"),
            @ApiResponse(responseCode = "400", description = "Search name or source filter is invalid"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing players:read permission"),
            @ApiResponse(responseCode = "500", description = "Unexpected query failure")
    })
    public ResponseEntity<?> findPlayersByStringInName(
            @RequestParam("name") String searchString,
            @RequestParam(value = "source", required = false) String source) {
        String normalizedSearch = searchString == null ? null : searchString.trim();
        if (normalizedSearch == null || normalizedSearch.length() < 2) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Search name must contain at least 2 characters"));
        }
        ImportSource importSource;
        try {
            importSource = parseSource(source);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Unknown source filter: " + source));
        }
        FindFederatedPlayersByStringInNameQuery query =
                new FindFederatedPlayersByStringInNameQuery(normalizedSearch, importSource);
        var queryResponse = queryBus.push(query);
        if (queryResponse.isSuccess()) {
            List<PlayerDto> playerDtos = ((List<?>) queryResponse.getResponse()).stream()
                    .map(player -> PlayerDto.fromObject((PlayerSearchReadModel) player))
                    .toList();
            return ResponseEntity.ok(playerDtos);
        } else {
            return ResponseEntity.status(500).build();
        }
    }

    public ResponseEntity<?> findPlayersByStringInName(String searchString) {
        return findPlayersByStringInName(searchString, null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('players:read')")
    @Operation(summary = "Get canonical player details",
            description = "Returns a canonical player and source-scoped registrations, clubs, competitions and matches")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Player details returned"),
            @ApiResponse(responseCode = "400", description = "Malformed UUID or invalid filter"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing players:read permission"),
            @ApiResponse(responseCode = "404", description = "Player not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected query failure")
    })
    public ResponseEntity<?> findPlayerDetailsById(
            @PathVariable("id") UUID id,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "season", required = false) String season,
            @RequestParam(value = "competition", required = false) String competition) {
        ImportSource parsedSource;
        try {
            parsedSource = parseSource(source);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Unknown source filter: " + source));
        }

        Season parsedSeason;
        try {
            parsedSeason = parseSeason(season);
        } catch (DateTimeException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Invalid season filter: " + season));
        }
        if (competition != null && competition.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Competition must not be blank"));
        }

        String parsedCompetition = competition == null ? null : competition.trim();
        var queryResponse = queryBus.push(new FindPlayerDetailsQuery(
                id, parsedSource, parsedSeason, parsedCompetition));
        if (queryResponse.isSuccess() && queryResponse.getResponse() instanceof PlayerDetailsReadModel details) {
            if (parsedCompetition != null && details.competitions().stream()
                    .noneMatch(item -> parsedCompetition.equals(item.name()))) {
                return ResponseEntity.badRequest()
                        .body(new ErrorMessage("Unknown competition filter: " + parsedCompetition));
            }
            return ResponseEntity.ok(PlayerDetailsDto.fromObject(details));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorMessage("Player not found: " + id));
    }

    public ResponseEntity<?> findPlayerDetailsById(UUID id) {
        return findPlayerDetailsById(id, null, null, null);
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

    public record ErrorMessage(String message) {
    }
}
