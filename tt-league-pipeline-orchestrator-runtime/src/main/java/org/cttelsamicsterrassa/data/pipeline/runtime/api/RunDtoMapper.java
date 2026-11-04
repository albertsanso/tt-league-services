package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayEligibility;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.UnitRetryEligibility;
import org.springframework.stereotype.Component;

/**
 * Maps core runs, units and steps to the API shapes; durations of active runs, units and steps are measured up to now.
 * The statuses, the eligibility answers and the progress figures come from the core values: nothing is derived here
 * except display figures (a percentage, a folder) and the sum of the import counters over the units.
 */
@Component
public class RunDtoMapper {

    private static final String PACKAGE_PATH = "/api/pipeline/runs/%s/units/%s/package";

    private final RunClock clock;

    public RunDtoMapper(RunClock clock) {
        this.clock = clock;
    }

    /** {@code steps} are the run's step attempts, or null to leave {@code steps} out; the units are not listed. */
    public RunSummaryDto summary(PipelineRun run, List<PipelineStep> steps) {
        return summary(run, steps, steps, null, false);
    }

    /**
     * The summary with the run's units in their summary form; {@code units} null leaves them out (the ingest run and
     * import job ids are then unknown too).
     */
    public RunSummaryDto summary(PipelineRun run, List<PipelineStep> steps, List<RunUnit> units) {
        return summary(run, steps, steps, units, true);
    }

    private RunSummaryDto summary(PipelineRun run, List<PipelineStep> listedSteps, List<PipelineStep> knownSteps,
            List<RunUnit> units, boolean listUnits) {
        RunUnit single = units != null && units.size() == 1 ? units.get(0) : null;
        UUID importJobId = single == null ? null : single.importJobId();
        UUID current = units == null ? null : units.stream()
                .filter(unit -> unit.status().isRunning())
                .map(RunUnit::id)
                .findFirst()
                .orElse(null);
        return new RunSummaryDto(
                run.id(), run.source().name(), run.season(),
                run.scope().filters().stream().map(ScopeFilterDto::from).toList(), run.scope().isFullSeason(),
                run.trigger().name(), run.requestedBy(), run.force(), run.status().name(), run.createdAt(),
                run.startedAt(), run.finishedAt(), duration(run.startedAt(), run.finishedAt()),
                ErrorDto.from(run.error()), single == null ? null : single.ingestRunId(), importJobId,
                run.retryOfRunId(), run.retryOfUnitId(), importJobReused(importJobId, knownSteps), current,
                listUnits && units != null ? units.stream().map(this::unit).toList() : null,
                listedSteps == null ? null : latestAttempts(listedSteps));
    }

    public StepDto step(PipelineStep step) {
        return new StepDto(step.runId(), step.unitId(), step.kind().name(), step.attempt(), step.status().name(),
                step.startedAt(), step.finishedAt(), duration(step.startedAt(), step.finishedAt()),
                step.externalRef(), step.outcome(), step.retryable(), ErrorDto.from(step.error()),
                health(step), step.importJobReused());
    }

    /** The summary form of a unit, without import counters. */
    public RunUnitDto unit(RunUnit unit) {
        return unit(unit, null);
    }

    /** A unit with the import counters of its platform job; {@code report} may be null. */
    public RunUnitDto unit(RunUnit unit, ImportReport report) {
        return new RunUnitDto(unit.runId(), unit.id(), unit.ordinal(), unit.unitKey(), unit.label(),
                unit.scope().filters().stream().map(ScopeFilterDto::from).toList(), unit.status().name(),
                unit.startedAt(), unit.finishedAt(), duration(unit.startedAt(), unit.finishedAt()),
                ErrorDto.from(unit.error()), unit.ingestRunId(), unit.importJobId(), progress(unit.progress()),
                report == null ? null : importReport(report));
    }

    /**
     * The detail of a run. {@code retry} and {@code retriedBy} are keyed by unit id; a unit missing from them is
     * reported as not retryable by anyone yet.
     */
    public RunDetailDto detail(
            PipelineRun run,
            List<RunUnit> units,
            List<PipelineStep> steps,
            List<RunArtifact> artifacts,
            List<ImportReport> reports,
            ReplayEligibility replay,
            Map<UUID, UnitRetryEligibility> retry,
            Map<UUID, List<PipelineRun>> retriedBy) {
        Map<UUID, ImportReport> reportByUnit = new HashMap<>();
        reports.forEach(report -> reportByUnit.put(report.unitId(), report));
        List<RunUnitDetailDto> details = units.stream()
                .map(unit -> unitDetail(unit, reportByUnit.get(unit.id()), steps, artifacts,
                        retry.get(unit.id()), retriedBy.getOrDefault(unit.id(), List.of())))
                .toList();
        return new RunDetailDto(
                summary(run, null, steps, units, false),
                details,
                steps.stream().map(this::step).toList(),
                artifacts.stream().map(this::artifact).toList(),
                reports.isEmpty() ? null : importReport(reports),
                issues(run, units, reports),
                ReplayDto.from(replay));
    }

    private RunUnitDetailDto unitDetail(
            RunUnit unit,
            ImportReport report,
            List<PipelineStep> steps,
            List<RunArtifact> artifacts,
            UnitRetryEligibility retry,
            List<PipelineRun> retries) {
        List<RunArtifact> own = artifacts.stream().filter(a -> a.unitId().equals(unit.id())).toList();
        Optional<RunArtifact> zip = own.stream()
                .filter(a -> a.kind() == ArtifactKind.ZIP)
                .findFirst();
        Optional<RunArtifact> available = zip.filter(a -> !a.isPurged());
        return new RunUnitDetailDto(
                unit(unit, report),
                steps.stream().filter(step -> step.unitId().equals(unit.id())).map(this::step).toList(),
                own.stream().map(this::artifact).toList(),
                zip.map(a -> folder(a.storageKey())).orElse(null),
                available.map(a -> PACKAGE_PATH.formatted(unit.runId(), unit.id())).orElse(null),
                retry == null ? new RunUnitDetailDto.Retry(false, null) : RunUnitDetailDto.Retry.from(retry),
                retries.stream()
                        .map(r -> new RunUnitDetailDto.RetryRef(r.id(), r.status().name()))
                        .toList());
    }

    private ArtifactDto artifact(RunArtifact a) {
        return new ArtifactDto(a.unitId(), a.kind().name(), a.sha256(), a.sizeBytes(), a.createdAt(), a.purgedAt());
    }

    /** The parent folder of a storage key ({@code source/season/run/ordinal-key}); the key itself when it has none. */
    private static String folder(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash < 0 ? storageKey : storageKey.substring(0, slash);
    }

    private static List<String> issues(PipelineRun run, List<RunUnit> units, List<ImportReport> reports) {
        Set<String> issues = new LinkedHashSet<>();
        boolean several = units.size() > 1;
        Map<UUID, String> labels = new HashMap<>();
        units.forEach(unit -> labels.put(unit.id(), unit.label()));
        for (ImportReport report : reports) {
            String prefix = several ? labels.getOrDefault(report.unitId(), "unit") + ": " : "";
            report.issues().forEach(issue -> issues.add(prefix + issue));
        }
        for (RunUnit unit : units) {
            if (unit.error() != null && unit.status() == UnitStatus.FAILED) {
                issues.add((several ? unit.label() + ": " : "") + unit.error().message());
            }
        }
        if (run.status() == RunStatus.FAILED && run.error() != null && !several) {
            issues.add(run.error().message());
        }
        return new ArrayList<>(issues);
    }

    private UnitProgressDto progress(UnitProgress progress) {
        if (progress == null) {
            return null;
        }
        Integer percent = progress.itemsTotal() == null || progress.itemsTotal() == 0
                ? null
                : (int) Math.min(100L, progress.itemsProcessed() * 100L / progress.itemsTotal());
        return new UnitProgressDto(progress.step().name(), progress.stage(), progress.itemsProcessed(),
                progress.itemsTotal(), percent, progress.currentItem(), progress.updatedAt());
    }

    /** The flag of the IMPORT step whose external reference is the given import job; null when there is none. */
    private static Boolean importJobReused(UUID importJobId, List<PipelineStep> steps) {
        if (steps == null || importJobId == null) {
            return null;
        }
        String job = importJobId.toString();
        return steps.stream()
                .filter(step -> step.kind() == StepKind.IMPORT && job.equals(step.externalRef()))
                .map(PipelineStep::importJobReused)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static ImportReportDto importReport(ImportReport report) {
        return new ImportReportDto(report.importStatus(), report.filesSeen(), report.itemsPersisted(),
                report.skipped(), report.processorFailures(), report.scheduledCreated(), report.upgradedToPlayed(),
                report.rescheduled(), report.partialActas(), report.invalidActas(),
                report.unresolvedPendingFixtures(), report.amendedPlayed(), report.receivedAt());
    }

    /**
     * The counters summed over the reports of a run. One report maps as it is; several report SUCCEEDED or FAILED
     * when every unit agrees and PARTIAL otherwise.
     */
    private static ImportReportDto importReport(List<ImportReport> reports) {
        if (reports.size() == 1) {
            return importReport(reports.get(0));
        }
        Set<String> statuses = new LinkedHashSet<>();
        reports.forEach(report -> statuses.add(report.importStatus()));
        String status = statuses.size() == 1 ? statuses.iterator().next() : "PARTIAL";
        Instant received = reports.stream().map(ImportReport::receivedAt).max(Comparator.naturalOrder()).orElseThrow();
        return new ImportReportDto(status,
                sum(reports, ImportReport::filesSeen), sum(reports, ImportReport::itemsPersisted),
                sum(reports, ImportReport::skipped), sum(reports, ImportReport::processorFailures),
                sum(reports, ImportReport::scheduledCreated), sum(reports, ImportReport::upgradedToPlayed),
                sum(reports, ImportReport::rescheduled), sum(reports, ImportReport::partialActas),
                sum(reports, ImportReport::invalidActas), sum(reports, ImportReport::unresolvedPendingFixtures),
                sum(reports, ImportReport::amendedPlayed), received);
    }

    private static long sum(List<ImportReport> reports, ToLongFunction<ImportReport> field) {
        return reports.stream().mapToLong(field).sum();
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
            latest.merge(step.kind(), step, RunDtoMapper::newer);
        }
        return latest.values().stream()
                .sorted(Comparator.comparing(PipelineStep::kind))
                .map(step -> new StepStatusDto(step.kind().name(), step.status().name(), step.attempt()))
                .toList();
    }

    /** The more recent attempt: units run one after the other, so the latest start is the latest attempt of the run. */
    private static PipelineStep newer(PipelineStep a, PipelineStep b) {
        int byStart = a.startedAt().compareTo(b.startedAt());
        if (byStart != 0) {
            return byStart > 0 ? a : b;
        }
        return a.attempt() >= b.attempt() ? a : b;
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
