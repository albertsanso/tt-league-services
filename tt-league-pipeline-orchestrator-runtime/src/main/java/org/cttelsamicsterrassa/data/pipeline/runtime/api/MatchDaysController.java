package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayNotFoundException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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

    private final MatchDayQueryService queries;
    private final MatchDayActions actions;

    MatchDaysController(MatchDayQueryService queries, MatchDayActions actions) {
        this.queries = queries;
        this.actions = actions;
    }

    @GetMapping
    @Operation(summary = "List tracked match days, by first date then competition, group, phase and round",
            description = "from and to keep match days whose first-to-last match dates overlap the range.")
    PageDto<MatchDaySummaryDto> list(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "season", required = false) String season,
            @RequestParam(name = "state", required = false) String state,
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
        return queries.list(new MatchDayQuery(enumValue(source, PipelineSource.class, "source"), season(season),
                enumValue(state, MatchDayState.class, "state"), from, to, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Match day detail with its matches and timeline")
    @ApiResponse(responseCode = "404", description = "Unknown match day")
    MatchDayDetailDto detail(@PathVariable("id") UUID id) {
        return queries.detail(id).orElseThrow(() -> new MatchDayNotFoundException(id));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close a match day manually; the tracker never reopens it on its own")
    @ApiResponse(responseCode = "409", description = "The match day is already closed")
    MatchDayDetailDto close(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) MatchDayActionRequest body,
            Authentication authentication) {
        actions.close(id, CurrentUser.name(authentication), note(body));
        return reloaded(id);
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reopen a closed match day")
    @ApiResponse(responseCode = "409", description = "The match day is not closed")
    MatchDayDetailDto reopen(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) MatchDayActionRequest body,
            Authentication authentication) {
        actions.reopen(id, CurrentUser.name(authentication), note(body));
        return reloaded(id);
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
        return reloaded(id);
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
        return reloaded(id);
    }

    @PostMapping(value = "/{id}/notes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a note to a match day, or to one of its matches")
    MatchDayDetailDto addNote(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MatchDayNoteRequest body,
            Authentication authentication) {
        actions.addNote(id, body.matchId(), CurrentUser.name(authentication), body.text());
        return reloaded(id);
    }

    private MatchDayDetailDto reloaded(UUID id) {
        return queries.detail(id).orElseThrow(() -> new MatchDayNotFoundException(id));
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
