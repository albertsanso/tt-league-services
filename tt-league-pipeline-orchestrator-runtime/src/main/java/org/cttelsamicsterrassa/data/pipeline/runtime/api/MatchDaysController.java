package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.polling.MatchDayRefresh;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayNotFoundException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.MatchDayChangeListener;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tracked match days. Reads are open to any authenticated user; the actions need the {@code matches:write} authority
 * (see the security configuration) and record the JWT subject as the actor. Every action answers with the updated
 * detail.
 */
@RestController
@RequestMapping("/api/pipeline/match-days")
@Tag(name = "Match days")
@SecurityRequirement(name = "bearer")
class MatchDaysController {

    private static final Logger LOG = LoggerFactory.getLogger(MatchDaysController.class);

    private final MatchDayQueryService queries;
    private final MatchDayResultsService results;
    private final MatchDayActions actions;
    private final MatchDayRefresh refresh;
    private final RunDtoMapper mapper;
    private final PipelineOrchestratorProperties.Triggers triggers;
    private final MatchDayChangeListener changes;

    MatchDaysController(
            MatchDayQueryService queries,
            MatchDayResultsService results,
            MatchDayActions actions,
            MatchDayRefresh refresh,
            RunDtoMapper mapper,
            PipelineOrchestratorProperties.Triggers triggers,
            MatchDayChangeListener changes) {
        this.queries = queries;
        this.results = results;
        this.actions = actions;
        this.refresh = refresh;
        this.mapper = mapper;
        this.triggers = triggers;
        this.changes = changes;
    }

    @GetMapping
    @Operation(summary = "List tracked match days, by first date then competition, group, phase and round",
            description = "from and to keep match days whose first-to-last match dates overlap the range; "
                    + "competition and phase are exact matches (see /facets); undated=true keeps only match days "
                    + "without dates and cannot be combined with from or to.")
    PageDto<MatchDaySummaryDto> list(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "season", required = false) String season,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "competition", required = false) String competition,
            @RequestParam(name = "phase", required = false) String phase,
            @RequestParam(name = "undated", defaultValue = "false") boolean undated,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        if (page < 0) {
            throw new InvalidRequestException("page", "page must not be negative");
        }
        if (size < 1 || size > MatchDayQuery.MAX_SIZE) {
            throw new InvalidRequestException("size", "size must be between 1 and " + MatchDayQuery.MAX_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from", "from must not be after to");
        }
        if (undated && (from != null || to != null)) {
            throw new InvalidRequestException("undated", "undated cannot be combined with from or to");
        }
        return queries.list(new MatchDayQuery(enumValue(source, PipelineSource.class, "source"), season(season),
                enumValue(state, MatchDayState.class, "state"), text(competition, "competition"),
                text(phase, "phase"), undated, from, to, page, size));
    }

    @GetMapping("/facets")
    @Operation(summary = "Values the list can be filtered by",
            description = "seasons honour source; competitions and phases honour source and season.")
    MatchDayFacetsDto facets(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "season", required = false) String season) {
        return queries.facets(enumValue(source, PipelineSource.class, "source"), season(season));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Match day detail with its matches and timeline")
    @ApiResponse(responseCode = "404", description = "Unknown match day")
    MatchDayDetailDto detail(@PathVariable("id") UUID id) {
        return queries.detail(id).orElseThrow(() -> new MatchDayNotFoundException(id));
    }

    @GetMapping("/{id}/results")
    @Operation(summary = "Results of the matches of the match day, read from the platform on demand",
            description = "Never stored or cached by the orchestrator.")
    @ApiResponse(responseCode = "404", description = "Unknown match day")
    @ApiResponse(responseCode = "502", description = "The platform could not be read: PLATFORM_UNAVAILABLE")
    MatchDayResultsDto results(@PathVariable("id") UUID id) {
        return results.results(id).orElseThrow(() -> new MatchDayNotFoundException(id));
    }

    @PostMapping("/{id}/refresh")
    @Operation(summary = "Re-ingest the round of the match day in its group",
            description = "Creates a MANUAL run through the same path as POST /api/pipeline/runs, scoped to the round "
                    + "of the match day in its group (the whole category for RFETM). Needs matches:write.")
    @ApiResponse(responseCode = "201", description = "A run was created")
    @ApiResponse(responseCode = "202", description = "The refresh was queued behind the active run")
    @ApiResponse(responseCode = "404", description = "Unknown match day")
    @ApiResponse(responseCode = "409", description = "The source has an active run (or a pending trigger)")
    @ApiResponse(responseCode = "422", description = "The scope is unavailable: NO_INGEST_STATUS or SCOPE_UNMATCHED")
    ResponseEntity<Object> refresh(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) MatchDayRefreshRequest body,
            Authentication authentication) {
        MatchDaySummaryDto day = reloaded(id).matchDay();
        boolean force = body != null && body.force();
        List<Outcome> outcomes =
                refresh.refresh(id, force, CurrentUser.name(authentication), triggers.conflictMode());
        if (outcomes.stream().anyMatch(o -> o instanceof Outcome.Created || o instanceof Outcome.Queued)) {
            notifyChange(PipelineSource.valueOf(day.source()), day.season(), id);
        }
        return TriggerResponses.of(outcomes, mapper);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close a match day manually; the tracker never reopens it on its own")
    @ApiResponse(responseCode = "409", description = "The match day is already closed")
    MatchDayDetailDto close(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) MatchDayActionRequest body,
            Authentication authentication) {
        actions.close(id, CurrentUser.name(authentication), note(body));
        return changed(id);
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reopen a closed match day")
    @ApiResponse(responseCode = "409", description = "The match day is not closed")
    MatchDayDetailDto reopen(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) MatchDayActionRequest body,
            Authentication authentication) {
        actions.reopen(id, CurrentUser.name(authentication), note(body));
        return changed(id);
    }

    @PutMapping("/{id}/matches/{matchId}/ignore")
    @Operation(summary = "Ignore a match: it counts as resolved while its platform status keeps updating")
    @ApiResponse(responseCode = "409", description = "The match is already ignored")
    MatchDayDetailDto ignore(
            @PathVariable("id") UUID id,
            @PathVariable("matchId") UUID matchId,
            @Valid @RequestBody(required = false) MatchDayActionRequest body,
            Authentication authentication) {
        actions.ignoreMatch(id, matchId, CurrentUser.name(authentication), note(body));
        return changed(id);
    }

    @DeleteMapping("/{id}/matches/{matchId}/ignore")
    @Operation(summary = "Stop ignoring a match")
    @ApiResponse(responseCode = "409", description = "The match is not ignored")
    MatchDayDetailDto unignore(
            @PathVariable("id") UUID id,
            @PathVariable("matchId") UUID matchId,
            @Valid @RequestBody(required = false) MatchDayActionRequest body,
            Authentication authentication) {
        actions.unignoreMatch(id, matchId, CurrentUser.name(authentication), note(body));
        return changed(id);
    }

    @PostMapping(value = "/{id}/notes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a note to a match day, or to one of its matches")
    MatchDayDetailDto addNote(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MatchDayNoteRequest body,
            Authentication authentication) {
        actions.addNote(id, body.matchId(), CurrentUser.name(authentication), body.text());
        return changed(id);
    }

    private MatchDayDetailDto reloaded(UUID id) {
        return queries.detail(id).orElseThrow(() -> new MatchDayNotFoundException(id));
    }

    /** The updated detail after a successful action; live views are told to refetch. */
    private MatchDayDetailDto changed(UUID id) {
        MatchDayDetailDto detail = reloaded(id);
        notifyChange(PipelineSource.valueOf(detail.matchDay().source()), detail.matchDay().season(), id);
        return detail;
    }

    private void notifyChange(PipelineSource source, String season, UUID id) {
        try {
            changes.matchDaysChanged(source, season, id, MatchDayChangeListener.Cause.ACTION);
        } catch (RuntimeException e) {
            LOG.warn("match-day change listener failed for {} {}: {}", source, season, e.toString());
        }
    }

    private static String text(String value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > 255) {
            throw new InvalidRequestException(name, name + " must not be blank or longer than 255 characters");
        }
        return value;
    }

    private static String note(MatchDayActionRequest body) {
        return body == null ? null : body.normalizedNote();
    }

    private static String season(String season) {
        if (season == null) {
            return null;
        }
        try {
            return PipelineRun.requireValidSeason(season);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("season", e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, String name) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(name, "Invalid value for " + name + ": " + value
                    + " (expected one of " + Arrays.stream(type.getEnumConstants()).map(Enum::name)
                            .collect(Collectors.joining(", ")) + ")", e);
        }
    }
}
