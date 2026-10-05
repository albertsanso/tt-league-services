package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.springframework.stereotype.Component;

/** Maps core runs and steps to the API shapes; durations of active runs and steps are measured up to now. */
@Component
public class RunDtoMapper {

    private final RunClock clock;

    public RunDtoMapper(RunClock clock) {
        this.clock = clock;
    }

    /** {@code steps} are the run's step attempts, or null to leave {@code steps} out (events, detail view). */
    public RunSummaryDto summary(PipelineRun run, List<PipelineStep> steps) {
        return new RunSummaryDto(
                run.id(), run.source().name(), run.season(),
                run.scope().filters().stream().map(ScopeFilterDto::from).toList(), run.scope().isFullSeason(),
                run.trigger().name(), run.requestedBy(), run.force(), run.status().name(), run.createdAt(),
                run.startedAt(), run.finishedAt(), duration(run.startedAt(), run.finishedAt()),
                ErrorDto.from(run.error()), run.ingestRunId(), run.importJobId(), run.retryOfRunId(),
                steps == null ? null : latestAttempts(steps));
    }

    public StepDto step(PipelineStep step) {
        return new StepDto(step.runId(), step.kind().name(), step.attempt(), step.status().name(),
                step.startedAt(), step.finishedAt(), duration(step.startedAt(), step.finishedAt()),
                step.externalRef(), step.outcome(), step.retryable(), ErrorDto.from(step.error()),
                health(step));
    }

    public RunDetailDto detail(PipelineRun run, List<PipelineStep> steps, List<RunArtifact> artifacts,
            ImportReport report) {
        List<String> issues = new ArrayList<>();
        if (report != null) {
            issues.addAll(report.issues());
        }
        if (run.status() == RunStatus.FAILED && run.error() != null) {
            issues.add(run.error().message());
        }
        return new RunDetailDto(
                summary(run, null),
                steps.stream().map(this::step).toList(),
                artifacts.stream().map(a -> new ArtifactDto(a.kind().name(), a.sha256(), a.sizeBytes(),
                        a.createdAt())).toList(),
                report == null ? null : importReport(report),
                issues);
    }

    private static ImportReportDto importReport(ImportReport report) {
        return new ImportReportDto(report.importStatus(), report.filesSeen(), report.itemsPersisted(),
                report.skipped(), report.processorFailures(), report.scheduledCreated(), report.upgradedToPlayed(),
                report.rescheduled(), report.partialActas(), report.invalidActas(),
                report.unresolvedPendingFixtures(), report.amendedPlayed(), report.receivedAt());
    }

    private static StepDto.Health health(PipelineStep step) {
        return step.ingestHealth() == null
                ? null
                : new StepDto.Health(step.ingestHealth().httpErrors(), step.ingestHealth().timeouts(),
                        step.ingestHealth().parseErrors());
    }

    private static List<StepStatusDto> latestAttempts(List<PipelineStep> steps) {
        Map<StepKind, PipelineStep> latest = new EnumMap<>(StepKind.class);
        for (PipelineStep step : steps) {
            latest.merge(step.kind(), step, (a, b) -> a.attempt() >= b.attempt() ? a : b);
        }
        return latest.values().stream()
                .sorted(Comparator.comparing(PipelineStep::kind))
                .map(step -> new StepStatusDto(step.kind().name(), step.status().name(), step.attempt()))
                .toList();
    }

    /** Finished minus started; for an unfinished item now minus started; null before it started. */
    private Long duration(Instant started, Instant finished) {
        if (started == null) {
            return null;
        }
        Instant end = finished != null ? finished : clock.now();
        return Math.max(0L, Duration.between(started, end).toMillis());
    }
}
