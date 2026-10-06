package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.RetryUnit;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.UnitRetryEligibility;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipeline/runs")
@Tag(name = "Runs")
@SecurityRequirement(name = "bearer")
class RunsController {

    private static final String ALL = "ALL";

    private final TriggerRun triggerRun;
    private final ReplayRun replayRun;
    private final RetryUnit retryUnit;
    private final UnitPackageService packages;
    private final RunQueryService queries;
    private final RunDtoMapper mapper;
    private final PipelineOrchestratorProperties.Triggers triggers;

    RunsController(TriggerRun triggerRun, ReplayRun replayRun, RetryUnit retryUnit, UnitPackageService packages,
            RunQueryService queries, RunDtoMapper mapper, PipelineOrchestratorProperties.Triggers triggers) {
        this.triggerRun = triggerRun;
        this.replayRun = replayRun;
        this.retryUnit = retryUnit;
        this.packages = packages;
        this.queries = queries;
        this.mapper = mapper;
        this.triggers = triggers;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Trigger runs", description = "Starts one MANUAL run per source (source ALL = every source). "
            + "Needs the matches:write authority.")
    @ApiResponse(responseCode = "201", description = "At least one run was created")
    @ApiResponse(responseCode = "202", description = "No run was created but a trigger was queued")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Every source has an active run (or a pending trigger)")
    @ApiResponse(responseCode = "422", description = "The scope is unavailable: NO_OPEN_MATCH_DAYS, NO_INGEST_STATUS or SCOPE_UNMATCHED")
    ResponseEntity<Object> trigger(@Valid @RequestBody TriggerRunRequest body, Authentication authentication) {
        TriggerRun.Command command = command(body, CurrentUser.name(authentication));
        return TriggerResponses.of(triggerRun.trigger(command), mapper);
    }

    @GetMapping
    @Operation(summary = "List runs, newest first",
            description = "unitKey keeps the runs that have a unit with that key.")
    PageDto<RunSummaryDto> list(
            @RequestParam(name = "source", required = false) List<String> sources,
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(name = "unitKey", required = false) String unitKey,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidRequestException("page", "page must not be negative");
        }
        if (size < 1 || size > RunQuery.MAX_SIZE) {
            throw new InvalidRequestException("size", "size must be between 1 and " + RunQuery.MAX_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from", "from must not be after to");
        }
        if (unitKey != null && (unitKey.isBlank() || unitKey.length() > 64)) {
            throw new InvalidRequestException("unitKey", "unitKey must be 1 to 64 characters");
        }
        return queries.list(new RunQuery(enums(sources, PipelineSource.class, "source"),
                enums(statuses, RunStatus.class, "status"), from, to, unitKey, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Run detail with units, steps, artifacts, import report and issues")
    @ApiResponse(responseCode = "404", description = "Unknown run")
    RunDetailDto detail(@PathVariable("id") UUID id) {
        return queries.detail(id).orElseThrow(() -> new RunNotFoundException(id));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay the import of a run",
            description = "Creates a RETRY run that re-submits the stored package of the run, without calling ingest. "
                    + "The platform returns the existing import job when it already imported the same content. "
                    + "Needs the matches:write authority.")
    @ApiResponse(responseCode = "201", description = "The replay run was created")
    @ApiResponse(responseCode = "404", description = "Unknown run")
    @ApiResponse(responseCode = "409", description = "The source has an active run (code ACTIVE_RUN)")
    @ApiResponse(responseCode = "422", description = "The run cannot be replayed: RUN_ACTIVE, NO_PACKAGE or ARTIFACT_PURGED")
    ResponseEntity<Object> replay(@PathVariable("id") UUID id, Authentication authentication) {
        ReplayRun.Outcome outcome = replayRun.replay(id, CurrentUser.name(authentication));
        return switch (outcome) {
            case ReplayRun.Created created -> ResponseEntity
                    .created(URI.create("/api/pipeline/runs/" + created.run().id()))
                    .body(mapper.summary(created.run(), List.of()));
            case ReplayRun.NotFound notFound -> throw new RunNotFoundException(id);
            case ReplayRun.NotReplayable notReplayable -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                    notReplayable.code(), notReplayable.message(), null);
            case ReplayRun.Rejected rejected -> problem(HttpStatus.CONFLICT, rejected.code(), rejected.message(),
                    rejected.activeRunId());
        };
    }

    @PostMapping("/{id}/units/{unitId}/retry")
    @Operation(summary = "Retry one failed or skipped unit",
            description = "Creates a UNIT_RETRY run that re-runs ingest, fetch and import for the scope of the unit "
                    + "without touching the original run. Needs the matches:write authority.")
    @ApiResponse(responseCode = "202", description = "The retry run was created")
    @ApiResponse(responseCode = "404", description = "Unknown run or unit, or the unit belongs to another run")
    @ApiResponse(responseCode = "409", description = "The run or its source has an active run (code RUN_ACTIVE)")
    @ApiResponse(responseCode = "422", description = "The unit is not FAILED or SKIPPED (code UNIT_NOT_RETRYABLE)")
    ResponseEntity<Object> retryUnit(@PathVariable("id") UUID id, @PathVariable("unitId") UUID unitId,
            Authentication authentication) {
        RetryUnit.Outcome outcome = retryUnit.retry(id, unitId, CurrentUser.name(authentication));
        return switch (outcome) {
            case RetryUnit.Created created -> ResponseEntity
                    .accepted()
                    .location(URI.create("/api/pipeline/runs/" + created.run().id()))
                    .body(new UnitRetryResponse(created.run().id()));
            case RetryUnit.NotFound notFound -> throw new RunNotFoundException(id, unitId);
            case RetryUnit.NotRetryable notRetryable -> problem(
                    UnitRetryEligibility.RUN_ACTIVE.equals(notRetryable.code())
                            ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
                    notRetryable.code(), notRetryable.message(), null);
            case RetryUnit.Rejected rejected -> problem(HttpStatus.CONFLICT, UnitRetryEligibility.RUN_ACTIVE,
                    rejected.message(), rejected.activeRunId());
        };
    }

    @GetMapping("/{id}/units/{unitId}/package")
    @Operation(summary = "Download the stored ZIP package of a unit")
    @ApiResponse(responseCode = "404", description = "Unknown run or unit, or the unit has no package (NO_PACKAGE)")
    @ApiResponse(responseCode = "410", description = "The package was purged (ARTIFACT_PURGED)")
    ResponseEntity<Object> unitPackage(@PathVariable("id") UUID id, @PathVariable("unitId") UUID unitId) {
        UnitPackageService.Result result = packages.find(id, unitId);
        return switch (result) {
            case UnitPackageService.UnitMissing missing -> throw new RunNotFoundException(id, unitId);
            case UnitPackageService.NoPackage none -> problem(HttpStatus.NOT_FOUND, "NO_PACKAGE",
                    "Unit " + unitId + " has no stored package", null);
            case UnitPackageService.Purged purged -> problem(HttpStatus.GONE, "ARTIFACT_PURGED",
                    "The stored package of unit " + unitId + " has been purged", null);
            case UnitPackageService.Available available -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(available.content().size())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(id + "-" + available.unit().ordinal() + ".zip").build().toString())
                    .header("X-Content-SHA256", available.artifact().sha256())
                    .body(new InputStreamResource(available.content().open()));
        };
    }

    /** The run created for a unit retry. */
    record UnitRetryResponse(UUID runId) {
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String code, String message, UUID activeRunId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        if (activeRunId != null) {
            problem.setProperty("activeRunId", activeRunId);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private TriggerRun.Command command(TriggerRunRequest body, String requestedBy) {
        List<PipelineSource> sources = sources(body.source());
        try {
            List<ScopeFilter> filters = body.filters() == null ? List.of()
                    : body.filters().stream().map(filter -> {
                        if (filter == null) {
                            throw new IllegalArgumentException("filters must not contain null entries");
                        }
                        return filter.toDomain();
                    }).toList();
            return new TriggerRun.Command(sources, body.season(), body.scopeType(), filters, body.force(),
                    RunTrigger.MANUAL, requestedBy, triggers.conflictMode());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(fieldOf(e.getMessage()), e.getMessage(), e);
        }
    }

    private static List<PipelineSource> sources(String source) {
        if (ALL.equalsIgnoreCase(source)) {
            return List.of(PipelineSource.values());
        }
        try {
            return List.of(PipelineSource.valueOf(source.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("source", "source must be one of "
                    + Arrays.toString(PipelineSource.values()) + " or ALL", e);
        }
    }

    private static String fieldOf(String message) {
        String text = message == null ? "" : message;
        if (text.startsWith("season")) {
            return "season";
        }
        if (text.contains("filter") || text.contains("matchDays")) {
            return "filters";
        }
        if (text.startsWith("scopeType") || text.contains("scopeType")) {
            return "scopeType";
        }
        return null;
    }

    private static <E extends Enum<E>> Set<E> enums(List<String> values, Class<E> type, String name) {
        Set<E> parsed = EnumSet.noneOf(type);
        if (values == null) {
            return parsed;
        }
        for (String value : values) {
            try {
                parsed.add(Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException(name, "Invalid value for " + name + ": " + value
                        + " (expected one of " + Arrays.stream(type.getEnumConstants()).map(Enum::name)
                                .collect(Collectors.joining(", ")) + ")", e);
            }
        }
        return parsed;
    }
}
