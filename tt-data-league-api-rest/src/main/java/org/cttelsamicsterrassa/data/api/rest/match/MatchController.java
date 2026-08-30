package org.cttelsamicsterrassa.data.api.rest.match;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.match.find.FindMatchDetailsQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.SearchMatchesQuery;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchPage;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.PlayerLocation;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@MatchOpenAPIv1Controller
public class MatchController {
    @Autowired
    private QueryBus queryBus;
    @Autowired
    private MatchRepository matchRepository;

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('matches:read')")
    public ResponseEntity<?> options(@RequestParam(name = "source") String source,
                                     @RequestParam(name = "season", required = false) String season) {
        try {
            ImportSource parsedSource = parseSource(source);
            var seasons = matchRepository.findAllSeasonsBySource(parsedSource);
            var competitions = season == null || season.isBlank()
                    ? java.util.List.<String>of()
                    : matchRepository.findAllCompetitionsBySourceAndSeason(
                            parsedSource, Season.fromFormatted(season.trim()));
            return ResponseEntity.ok(new OptionsResponse(seasons, competitions));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Invalid source filter"));
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('matches:read')")
    @Operation(summary = "Search matches", description = "Searches source-scoped matches with inclusive date bounds")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching matches returned"),
            @ApiResponse(responseCode = "400", description = "Mandatory filter or date is invalid"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Missing matches:read permission")
    })
    public ResponseEntity<?> search(
            @RequestParam(name = "source") String source, @RequestParam(name = "season") String season,
            @RequestParam(name = "competition") String competition,
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) LocalDate toDate,
            @RequestParam(name = "playerId", required = false) UUID playerId,
            @RequestParam(name = "playerLocation", required = false) String playerLocation,
            @RequestParam(name = "playerName", required = false) String playerName,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        MatchSearchCriteria criteria;
        try {
            criteria = new MatchSearchCriteria(parseSource(source), Season.fromFormatted(season.trim()),
                    competition, fromDate, toDate, playerId,
                    playerLocation == null ? null : PlayerLocation.valueOf(playerLocation.trim().toUpperCase(Locale.ROOT)),
                    playerName, page, pageSize);
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(new ErrorMessage("Invalid match filters"));
        }
        var response = queryBus.push(new SearchMatchesQuery(criteria));
        if (!response.isSuccess() || !(response.getResponse() instanceof MatchSearchPage result)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorMessage("Match search failed"));
        }
        return ResponseEntity.ok(new SearchResponse(result.matches().stream().map(MatchDto::from).toList(),
                result.total(), result.page(), result.pageSize(), result.hasNext()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('matches:read')")
    @Operation(summary = "Get match details")
    public ResponseEntity<?> details(@PathVariable(name = "id") UUID id) {
        var response = queryBus.push(new FindMatchDetailsQuery(id));
        if (response.isSuccess() && response.getResponse() instanceof MatchDetailReadModel details) {
            return ResponseEntity.ok(MatchDetailDto.from(details));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Match not found: " + id));
    }

    public record SearchResponse(java.util.List<MatchDto> matches, long total, int page, int pageSize,
                                 boolean hasNext) {
    }
    public record ErrorMessage(String message) {
    }
    public record OptionsResponse(java.util.List<String> seasons, java.util.List<String> competitions) {
    }

    private static ImportSource parseSource(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source is mandatory");
        }
        return ImportSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
