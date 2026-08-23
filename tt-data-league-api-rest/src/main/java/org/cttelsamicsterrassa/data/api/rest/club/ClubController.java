package org.cttelsamicsterrassa.data.api.rest.club;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubCompetitionDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.FindFederatedClubCompetitionDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.FindFederatedClubByIdQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.FindFederatedClubDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.club.find.FindFederatedClubsByStringInNameQuery;
import org.cttelsamicsterrassa.data.core.application.club.update.ModifyFederatedClubNameCommand;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ClubOpenAPIv1Controller
public class ClubController {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private CommandBus commandBus;

    @GetMapping("/find_by_id")
    @Operation(summary = "Find club by id", description = "Returns a club by its UUID")
    public ResponseEntity<ClubDto> findClubById(@RequestParam("id") UUID id) {
        FindFederatedClubByIdQuery findFederatedClubByIdQuery = new FindFederatedClubByIdQuery(id);
        DomainQueryResponse<FederatedClub> queryResponse = queryBus.push(findFederatedClubByIdQuery);
        return queryResponse.isSuccess() ?
                ResponseEntity.ok(ClubDto.fromObject(queryResponse.getResponse())) :
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get club details", description = "Returns a club, its season registrations, and competition results")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Club details returned"),
            @ApiResponse(responseCode = "400", description = "Malformed UUID"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing clubs:read permission"),
            @ApiResponse(responseCode = "404", description = "Club not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected query failure")
    })
    public ResponseEntity<?> findClubDetailsById(@PathVariable("id") UUID id) {
        DomainQueryResponse<FederatedClubDetailsReadModel> queryResponse =
                queryBus.push(new FindFederatedClubDetailsQuery(id));
        return queryResponse.isSuccess()
                ? ResponseEntity.ok(ClubDetailsDto.fromObject(queryResponse.getResponse()))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Club not found: " + id));
    }

    @GetMapping("/{id}/competition/{season}/{competition}")
    @PreAuthorize("hasAuthority('clubs:read') and hasAuthority('matches:read')")
    @Operation(summary = "Get club competition details",
            description = "Returns the selected club competition and its match results")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Competition details returned"),
            @ApiResponse(responseCode = "400", description = "Malformed UUID or invalid filter"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing clubs:read or matches:read permission"),
            @ApiResponse(responseCode = "404", description = "Club or competition not found")
    })
    public ResponseEntity<?> findClubCompetitionDetails(
            @PathVariable("id") UUID id,
            @PathVariable String season,
            @PathVariable String competition) {
        Season parsedSeason;
        try {
            parsedSeason = Season.fromFormatted(season);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Invalid season filter: " + season));
        }
        if (competition == null || competition.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Competition must not be blank"));
        }

        DomainQueryResponse<FederatedClubCompetitionDetailsReadModel> queryResponse =
                queryBus.push(new FindFederatedClubCompetitionDetailsQuery(id, parsedSeason, competition));
        return queryResponse.isSuccess()
                ? ResponseEntity.ok(ClubCompetitionDetailsDto.fromObject(queryResponse.getResponse()))
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorMessage("Competition not found: " + competition));
    }

    @GetMapping("/search_in_name")
    @Operation(summary = "Search clubs by similar name", description = "Returns a list of clubs whose names are similar to the provided search string")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching clubs returned"),
            @ApiResponse(responseCode = "400", description = "Search name or source filter is invalid"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing clubs:read permission"),
            @ApiResponse(responseCode = "500", description = "Unexpected query failure")
    })
    public ResponseEntity<?> findClubsByStringInName(
            @RequestParam("name") String searchString,
            @RequestParam(value = "source", required = false) String source) {
        String normalizedSearch = searchString == null ? null : searchString.trim();
        if (normalizedSearch == null || normalizedSearch.length() < 2) {
            return ResponseEntity.badRequest()
                    .body(new ErrorMessage("Search name must contain at least 2 characters"));
        }

        ImportSource importSource;
        try {
            importSource = parseSource(source);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new ErrorMessage("Unknown source filter: " + source));
        }

        FindFederatedClubsByStringInNameQuery query =
                new FindFederatedClubsByStringInNameQuery(normalizedSearch, importSource);
        DomainQueryResponse<List<FederatedClub>> queryResponse = queryBus.push(query);
        if (queryResponse.isSuccess()) {
            List<ClubDto> clubDtos = queryResponse.getResponse().stream()
                    .map(ClubDto::fromObject)
                    .toList();
            return ResponseEntity.ok(clubDtos);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<List<ClubDto>> findClubsByStringInName(String searchString) {
        ResponseEntity<?> response = findClubsByStringInName(searchString, null);
        if (response.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            List<ClubDto> body = (List<ClubDto>) response.getBody();
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(response.getStatusCode()).build();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modify a club name", description = "Changes the name of a club; administrators only")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Club name updated"),
            @ApiResponse(responseCode = "400", description = "Club name is invalid"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "Club not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected update failure")
    })
    public ResponseEntity<?> modifyClubName(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ModifyClubNameRequest request) {
        String name = request == null || request.name() == null ? null : request.name().trim();
        if (name == null || name.length() < 2) {
            return ResponseEntity.badRequest()
                    .body(new ErrorMessage("Club name must contain at least 2 characters"));
        }

        ModifyFederatedClubNameCommand command = new ModifyFederatedClubNameCommand(
                ZonedDateTime.now(),
                UUID.randomUUID().toString(),
                id,
                name,
                null);
        DomainCommandResponse commandResponse = commandBus.push(command);
        if (commandResponse.isSuccess()) {
            return ResponseEntity.ok(ClubDto.fromObject(
                    (FederatedClub) commandResponse.getResponse()));
        }

        String error = String.valueOf(commandResponse.getResponse());
        if (error.startsWith("Club not found:")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(error));
        }
        return ResponseEntity.badRequest().body(new ErrorMessage(error));
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
