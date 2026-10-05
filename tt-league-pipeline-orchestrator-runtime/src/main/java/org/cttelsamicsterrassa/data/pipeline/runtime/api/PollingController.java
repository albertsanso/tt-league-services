package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettingsProvider;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.CurrentUser;
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
 * Adaptive polling policy and schedules. Reads are open to any authenticated user; changing or removing a policy needs
 * the {@code ADMIN} role and resuming a stopped unit needs {@code matches:write} (see the security configuration).
 */
@RestController
@RequestMapping("/api/pipeline/polling")
@Tag(name = "Polling")
@SecurityRequirement(name = "bearer")
class PollingController {

    private final PollingSettingsProvider settings;
    private final PollPolicyRepository policies;
    private final PollScheduleRepository schedules;
    private final RunClock clock;

    PollingController(
            PollingSettingsProvider settings,
            PollPolicyRepository policies,
            PollScheduleRepository schedules,
            RunClock clock) {
        this.settings = settings;
        this.policies = policies;
        this.schedules = schedules;
        this.clock = clock;
    }

    @GetMapping("/policies")
    @Operation(summary = "Effective polling settings of every source",
            description = "overridden is false when the configured defaults apply.")
    List<PollingPolicyDto> policies() {
        return Arrays.stream(PipelineSource.values()).map(settings::effective).map(PollingPolicyDto::from).toList();
    }

    @GetMapping("/policies/{source}")
    @Operation(summary = "Effective polling settings of one source")
    PollingPolicyDto policy(@PathVariable("source") String source) {
        return PollingPolicyDto.from(settings.effective(source(source)));
    }

    @PutMapping("/policies/{source}")
    @Operation(summary = "Replace the polling settings of a source", description = "Needs the ADMIN role. version is "
            + "the stored override version, 0 when the source has no override yet.")
    @ApiResponse(responseCode = "400", description = "Invalid settings")
    @ApiResponse(responseCode = "409", description = "STALE_POLICY: the override changed; reload and retry")
    PollingPolicyDto replacePolicy(
            @PathVariable("source") String source,
            @Valid @RequestBody PollingPolicyRequest body,
            Authentication authentication) {
        PipelineSource parsed = source(source);
        if (body.version() < 0) {
            throw new InvalidRequestException("version", "version must not be negative");
        }
        PollingSettings newSettings;
        try {
            newSettings = body.toSettings();
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(null, e.getMessage(), e);
        }
        policies.save(parsed, newSettings, CurrentUser.name(authentication), clock.now(), body.version());
        return PollingPolicyDto.from(settings.effective(parsed));
    }

    @DeleteMapping("/policies/{source}")
    @Operation(summary = "Remove the override of a source so the configured defaults apply again",
            description = "Needs the ADMIN role.")
    PollingPolicyDto deletePolicy(@PathVariable("source") String source) {
        PipelineSource parsed = source(source);
        policies.delete(parsed);
        return PollingPolicyDto.from(settings.effective(parsed));
    }

    @GetMapping("/schedules")
    @Operation(summary = "Poll schedules with level, interval, counters, next run, pending run and stop state")
    List<PollScheduleDto> schedules(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "season", required = false) String season) {
        return schedules.query(optionalSource(source), season(season)).stream().map(PollScheduleDto::from).toList();
    }

    @PostMapping("/schedules/{id}/resume")
    @Operation(summary = "Resume a stopped unit: it is due now and polled once before it can stop again",
            description = "Needs the matches:write authority.")
    @ApiResponse(responseCode = "404", description = "Unknown schedule")
    @ApiResponse(responseCode = "409", description = "The unit is not stopped, or it changed concurrently")
    PollScheduleDto resume(@PathVariable("id") UUID id) {
        PollSchedule schedule = schedules.findById(id).orElseThrow(() -> new PollScheduleNotFoundException(id));
        if (!schedule.isStopped()) {
            throw new PollScheduleNotStoppedException(id);
        }
        return PollScheduleDto.from(schedules.save(schedule.resumed(clock.now())));
    }

    private static PipelineSource source(String value) {
        PipelineSource source = optionalSource(value);
        if (source == null) {
            throw new InvalidRequestException("source", "source is required");
        }
        return source;
    }

    private static PipelineSource optionalSource(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PipelineSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("source", "Invalid value for source: " + value + " (expected one of "
                    + Arrays.stream(PipelineSource.values()).map(Enum::name).collect(Collectors.joining(", ")) + ")",
                    e);
        }
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
}
