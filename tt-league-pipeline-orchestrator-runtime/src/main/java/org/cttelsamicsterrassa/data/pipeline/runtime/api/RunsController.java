package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
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
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final RunQueryService queries;
    private final RunDtoMapper mapper;
    private final PipelineOrchestratorProperties.Triggers triggers;

    RunsController(TriggerRun triggerRun, RunQueryService queries, RunDtoMapper mapper,
            PipelineOrchestratorProperties.Triggers triggers) {
        this.triggerRun = triggerRun;
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
        List<Outcome> outcomes = triggerRun.trigger(command);
        TriggerResponse response = new TriggerResponse(outcomes.stream().map(this::result).toList());

        Outcome.Created firstCreated = outcomes.stream().filter(Outcome.Created.class::isInstance)
                .map(Outcome.Created.class::cast).findFirst().orElse(null);
        if (firstCreated != null) {
            URI location = URI.create("/api/pipeline/runs/" + firstCreated.run().id());
            return ResponseEntity.created(location).body(response);
        }
        if (outcomes.stream().anyMatch(Outcome.Queued.class::isInstance)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        HttpStatus status = outcomes.stream().anyMatch(Outcome.Rejected.class::isInstance)
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(status, response));
    }

    @GetMapping
    @Operation(summary = "List runs, newest first")
    PageDto<RunSummaryDto> list(
            @RequestParam(name = "source", required = false) List<String> sources,
            @RequestParam(name = "status", required = false) List<String> statuses,
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
        return queries.list(new RunQuery(enums(sources, PipelineSource.class, "source"),
                enums(statuses, RunStatus.class, "status"), from, to, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Run detail with steps, artifacts, import report and issues")
    @ApiResponse(responseCode = "404", description = "Unknown run")
    RunDetailDto detail(@PathVariable("id") UUID id) {
        return queries.detail(id).orElseThrow(() -> new RunNotFoundException(id));
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

    private TriggerResultDto result(Outcome outcome) {
        return switch (outcome) {
            case Outcome.Created created -> new TriggerResultDto(created.source().name(), "CREATED", null, null,
                    mapper.summary(created.run(), List.of()), null);
            case Outcome.Queued queued -> new TriggerResultDto(queued.source().name(), "QUEUED", null,
                    "Source " + queued.source() + " has an active run; the trigger is queued behind it", null,
                    queued.activeRunId());
            case Outcome.Rejected rejected -> new TriggerResultDto(rejected.source().name(), "REJECTED",
                    rejected.code(), rejected.message(), null, rejected.activeRunId());
            case Outcome.Unavailable unavailable -> new TriggerResultDto(unavailable.source().name(),
                    "UNAVAILABLE", unavailable.code(), unavailable.message(), null, null);
        };
    }

    private static ProblemDetail problem(HttpStatus status, TriggerResponse response) {
        List<String> messages = new ArrayList<>();
        String code = null;
        for (TriggerResultDto result : response.results()) {
            if (result.message() != null) {
                messages.add(result.message());
            }
            if (code == null) {
                code = result.code();
            }
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, String.join("; ", messages));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("results", response.results());
        return problem;
    }
}
