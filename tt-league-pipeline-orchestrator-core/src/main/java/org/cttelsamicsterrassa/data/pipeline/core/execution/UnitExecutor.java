package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStoreException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.FetchedPackage;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportJobState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSubmission;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestProgress;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;

/**
 * Drives one unit of a run through INGEST, FETCH_PACKAGE and IMPORT (or the replay shortcut). It continues from
 * whatever status and step rows the unit has stored, so it serves fresh and resumed units. Deadlines, attempts, retries
 * and failures are per unit; {@link RunExecutor} owns the run and the order of its units.
 */
final class UnitExecutor {

    private static final int MAX_STAGE = 64;

    private final RunUnitRepository units;
    private final PipelineStepRepository steps;
    private final RunArtifactRepository artifactRows;
    private final ImportReportRepository reports;
    private final IngestGateway ingest;
    private final ImportGateway importGateway;
    private final ArtifactStore artifacts;
    private final RunClock clock;
    private final RunObserver observer;
    private final ExecutionSettings settings;

    UnitExecutor(
            RunUnitRepository units,
            PipelineStepRepository steps,
            RunArtifactRepository artifactRows,
            ImportReportRepository reports,
            IngestGateway ingest,
            ImportGateway importGateway,
            ArtifactStore artifacts,
            RunClock clock,
            RunObserver observer,
            ExecutionSettings settings) {
        this.units = Objects.requireNonNull(units, "units is required");
        this.steps = Objects.requireNonNull(steps, "steps is required");
        this.artifactRows = Objects.requireNonNull(artifactRows, "artifactRows is required");
        this.reports = Objects.requireNonNull(reports, "reports is required");
        this.ingest = Objects.requireNonNull(ingest, "ingest is required");
        this.importGateway = Objects.requireNonNull(importGateway, "importGateway is required");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.observer = Objects.requireNonNull(observer, "observer is required");
        this.settings = Objects.requireNonNull(settings, "settings is required");
    }

    /** Result of one step attempt: the unit moved on, or the attempt's step row ended FAILED. */
    private sealed interface Outcome permits Advanced, Failed {
    }

    private record Advanced(RunUnit unit) implements Outcome {
    }

    private record Failed(RunUnit unit, PipelineStep step) implements Outcome {
    }

    /** Runs the unit until it is terminal and returns it. */
    RunUnit execute(PipelineRun run, RunUnit start) throws InterruptedException {
        RunUnit unit = start;
        while (!unit.status().isTerminal()) {
            unit = switch (unit.status()) {
                case PENDING -> run.trigger() == RunTrigger.RETRY ? replayPhase(run, unit) : ingestPhase(run, unit);
                case RUNNING_INGEST -> ingestPhase(run, unit);
                case PACKED -> packedPhase(run, unit);
                case IMPORTING -> importingPhase(run, unit);
                default -> throw new IllegalStateException("Unit " + unit.id() + " is in unexpected status "
                        + unit.status());
            };
        }
        return unit;
    }

    // ---------------------------------------------------------------- INGEST

    private RunUnit ingestPhase(PipelineRun run, RunUnit start) throws InterruptedException {
        RunUnit unit = start;
        List<PipelineStep> attempts = stepsOf(unit, StepKind.INGEST);
        PipelineStep latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Outcome pending = null;
        if (latest != null) {
            switch (latest.status()) {
                case RUNNING -> {
                    if (unit.status() == UnitStatus.RUNNING_INGEST && latest.externalRef() != null) {
                        Instant deadline = deadline(unit, StepKind.INGEST);
                        pending = pollIngest(unit, latest, deadline);
                    } else {
                        pending = new Failed(unit, interrupted(latest));
                    }
                }
                case SUCCEEDED -> {
                    return afterIngestSuccess(unit, latest);
                }
                case FAILED -> pending = new Failed(unit, latest);
            }
        }
        while (true) {
            Outcome outcome = pending != null ? pending : ingestAttempt(run, unit);
            pending = null;
            if (outcome instanceof Advanced advanced) {
                return advanced.unit();
            }
            Failed failed = (Failed) outcome;
            unit = failed.unit();
            Optional<Duration> wait = retryWait(unit, failed.step(), StepKind.INGEST);
            if (wait.isEmpty()) {
                return failUnit(unit, failed.step().error());
            }
            clock.sleep(wait.get());
        }
    }

    private RunUnit afterIngestSuccess(RunUnit unit, PipelineStep succeeded) {
        Instant now = clock.now();
        if ("NO_CHANGES".equals(succeeded.outcome())) {
            return updateUnit(unit.noChanges(now));
        }
        return updateUnit(unit.packed(now));
    }

    private Outcome ingestAttempt(PipelineRun run, RunUnit start) throws InterruptedException {
        RunUnit unit = start;
        List<PipelineStep> previous = stepsOf(unit, StepKind.INGEST);
        int attempt = nextAttempt(previous);
        PipelineStep step = saveStep(PipelineStep.start(
                UUID.randomUUID(), unit.runId(), unit.id(), StepKind.INGEST, attempt, clock.now(), null));
        Instant deadline = deadline(unit, StepKind.INGEST);

        String ingestRunId;
        try {
            ingestRunId = ingest.startRun(IngestRunRequest.forUnit(run, unit));
        } catch (GatewayException e) {
            return new Failed(unit, failStep(step, null, ingestStartCode(e), e.getMessage(),
                    e.kind() == GatewayException.Kind.UNAVAILABLE));
        }
        step = saveStep(step.withExternalRef(ingestRunId));
        Instant now = clock.now();
        unit = updateUnit(unit.status() == UnitStatus.PENDING
                ? unit.startIngest(ingestRunId, now)
                : unit.restartIngest(ingestRunId, now));
        return pollIngest(unit, step, deadline);
    }

    private static FailureCode ingestStartCode(GatewayException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> FailureCode.INGEST_UNAVAILABLE;
            case CONFLICT -> FailureCode.INGEST_BUSY;
            case REJECTED, NOT_FOUND -> FailureCode.INGEST_REJECTED;
            case PROTOCOL -> FailureCode.PROTOCOL_ERROR;
        };
    }

    private Outcome pollIngest(RunUnit start, PipelineStep step, Instant deadline) throws InterruptedException {
        RunUnit unit = start;
        String ingestRunId = unit.ingestRunId();
        int consecutiveFailures = 0;
        while (true) {
            if (!clock.now().isBefore(deadline)) {
                return new Failed(unit, failStep(step, null, FailureCode.STEP_TIMEOUT,
                        "Ingest run " + ingestRunId + " did not finish before the step timeout", false));
            }
            IngestRunState state;
            try {
                state = ingest.getRun(ingestRunId);
                consecutiveFailures = 0;
            } catch (GatewayException e) {
                switch (e.kind()) {
                    case NOT_FOUND -> {
                        return new Failed(unit, failStep(step, null, FailureCode.INGEST_RUN_LOST,
                                "Ingest run " + ingestRunId + " is unknown to the ingest service (restarted?)",
                                true));
                    }
                    case UNAVAILABLE -> {
                        consecutiveFailures++;
                        Optional<Duration> wait = settings.retry().delayBeforeRetry(consecutiveFailures - 1);
                        if (wait.isEmpty()) {
                            return new Failed(unit, failStep(step, null, FailureCode.INGEST_UNAVAILABLE,
                                    consecutiveFailures + " consecutive polls failed: " + e.getMessage(), true));
                        }
                        sleepUntil(wait.get(), deadline);
                        continue;
                    }
                    default -> {
                        return new Failed(unit, failStep(step, null, FailureCode.PROTOCOL_ERROR, e.getMessage(),
                                false));
                    }
                }
            }
            if (state.finished()) {
                return finishIngest(unit, step, state);
            }
            unit = reportIngestProgress(unit, state.progress());
            sleepUntil(settings.polls().ingest(), deadline);
        }
    }

    /** Writes the unit only when the ingest service reported different figures than the stored ones. */
    private RunUnit reportIngestProgress(RunUnit unit, IngestProgress reported) {
        if (reported == null) {
            return unit;
        }
        UnitProgress next = new UnitProgress(StepKind.INGEST, blankToNull(truncate(reported.stage(), MAX_STAGE)),
                reported.itemsProcessed(), reported.itemsTotal(),
                blankToNull(truncate(reported.currentItem(), UnitProgress.MAX_CURRENT_ITEM)), clock.now());
        if (next.sameFigures(unit.progress())) {
            return unit;
        }
        return updateUnit(unit.withProgress(next));
    }

    /** Marks the step a unit is in without item counts (fetch and import expose none); written once per step. */
    private RunUnit reportStep(RunUnit unit, StepKind kind) {
        UnitProgress next = new UnitProgress(kind, null, 0, null, null, clock.now());
        if (next.sameFigures(unit.progress())) {
            return unit;
        }
        return updateUnit(unit.withProgress(next));
    }

    private Outcome finishIngest(RunUnit unit, PipelineStep step, IngestRunState state) {
        String outcome = state.outcome();
        switch (outcome) {
            case "NO_CHANGES" -> {
                saveStep(step.succeed(clock.now(), "NO_CHANGES", state.health()));
                return new Advanced(updateUnit(unit.noChanges(clock.now())));
            }
            case "SUCCEEDED", "COMPLETED_WITH_ISSUES" -> {
                if (!state.packageAvailable()) {
                    return new Failed(unit, failStep(step, outcome, state.health(), FailureCode.INGEST_NO_PACKAGE,
                            "Ingest run " + state.ingestRunId() + " finished " + outcome + " without a package",
                            false));
                }
                saveStep(step.succeed(clock.now(), outcome, state.health()));
                return new Advanced(updateUnit(unit.packed(clock.now())));
            }
            case "SOURCE_UNAVAILABLE" -> {
                return new Failed(unit, failStep(step, outcome, state.health(), FailureCode.SOURCE_UNAVAILABLE,
                        orDefault(state.error(), "The source was unavailable for ingest run " + state.ingestRunId()),
                        true));
            }
            case "FAILED" -> {
                return new Failed(unit, failStep(step, outcome, state.health(), FailureCode.INGEST_FAILED,
                        orDefault(state.error(), "Ingest run " + state.ingestRunId() + " failed"), false));
            }
            default -> {
                return new Failed(unit, failStep(step, null, FailureCode.PROTOCOL_ERROR,
                        "Unexpected ingest outcome: " + truncate(outcome, 100), false));
            }
        }
    }

    // ---------------------------------------------------------------- REPLAY

    /**
     * Prepares a unit of a RETRY run: re-checks the stored package of the original's unit with the same ordinal, shares
     * it with the replay unit and skips ingest. A crash after the row copy resumes here and only repeats the checks.
     */
    private RunUnit replayPhase(PipelineRun run, RunUnit unit) {
        UUID originalRunId = run.retryOfRunId();
        Optional<RunArtifact> source = units.findByRunId(originalRunId).stream()
                .filter(original -> original.ordinal() == unit.ordinal())
                .findFirst()
                .flatMap(original -> artifactRows.findByUnitId(original.id()).stream()
                        .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                        .findFirst());
        if (source.isEmpty() || source.get().isPurged() || !artifacts.exists(source.get().storageKey())) {
            return failUnit(unit, new RunError(FailureCode.ARTIFACT_PURGED.name(),
                    "The stored package of run " + originalRunId + " unit " + unit.label()
                            + " is no longer available"));
        }
        RunArtifact original = source.get();
        try {
            if (!sha256Of(artifacts.content(original.storageKey())).equals(original.sha256())) {
                return failUnit(unit, new RunError(FailureCode.PACKAGE_CHECKSUM_MISMATCH.name(),
                        "The stored package of run " + originalRunId + " unit " + unit.label()
                                + " no longer matches its recorded SHA-256"));
            }
        } catch (ArtifactStoreException e) {
            return failUnit(unit, new RunError(FailureCode.ARTIFACT_STORE_FAILED.name(),
                    orDefault(e.getMessage(), FailureCode.ARTIFACT_STORE_FAILED.name())));
        }
        boolean copied = artifactRows.findByUnitId(unit.id()).stream()
                .anyMatch(artifact -> artifact.kind() == ArtifactKind.ZIP);
        if (!copied) {
            artifactRows.add(new RunArtifact(UUID.randomUUID(), unit.runId(), unit.id(), ArtifactKind.ZIP,
                    original.storageKey(), original.sha256(), original.sizeBytes(), clock.now()));
        }
        return updateUnit(unit.startReplay(clock.now()));
    }

    private static String sha256Of(ArtifactContent content) {
        try (InputStream in = content.open()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new ArtifactStoreException("Could not read the stored package", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // --------------------------------------------------------- FETCH_PACKAGE

    private RunUnit packedPhase(PipelineRun run, RunUnit unit) throws InterruptedException {
        Optional<RunArtifact> zip = artifactRows.findByUnitId(unit.id()).stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                .findFirst();
        if (zip.isEmpty()) {
            return fetchPackage(run, unit);
        }
        return importFromPacked(run, unit, zip.get());
    }

    private RunUnit fetchPackage(PipelineRun run, RunUnit start) throws InterruptedException {
        RunUnit unit = reportStep(start, StepKind.FETCH_PACKAGE);
        List<PipelineStep> attempts = stepsOf(unit, StepKind.FETCH_PACKAGE);
        PipelineStep latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Outcome pending = null;
        if (latest != null) {
            if (latest.status() == StepStatus.RUNNING) {
                pending = new Failed(unit, interrupted(latest));
            } else if (latest.status() == StepStatus.FAILED) {
                pending = new Failed(unit, latest);
            }
        }
        while (true) {
            Outcome outcome = pending != null ? pending : fetchAttempt(run, unit);
            pending = null;
            if (outcome instanceof Advanced advanced) {
                return advanced.unit();
            }
            Failed failed = (Failed) outcome;
            Optional<Duration> wait = retryWait(failed.unit(), failed.step(), StepKind.FETCH_PACKAGE);
            if (wait.isEmpty()) {
                return failUnit(failed.unit(), failed.step().error());
            }
            clock.sleep(wait.get());
        }
    }

    private Outcome fetchAttempt(PipelineRun run, RunUnit unit) {
        String key = storageKey(run, unit);
        int attempt = nextAttempt(stepsOf(unit, StepKind.FETCH_PACKAGE));
        PipelineStep step = saveStep(PipelineStep.start(UUID.randomUUID(), unit.runId(), unit.id(),
                StepKind.FETCH_PACKAGE, attempt, clock.now(), unit.ingestRunId()));
        FetchedPackage fetched;
        try {
            artifacts.delete(key);
            fetched = ingest.fetchPackage(unit.ingestRunId(), body -> artifacts.store(key, body));
        } catch (GatewayException e) {
            return new Failed(unit, failStep(step, null, packageCode(e), e.getMessage(),
                    e.kind() == GatewayException.Kind.UNAVAILABLE));
        } catch (ArtifactStoreException e) {
            return new Failed(unit, failStep(step, null, FailureCode.ARTIFACT_STORE_FAILED, e.getMessage(), false));
        }
        if (!fetched.stored().sha256().equals(fetched.declaredSha256())) {
            artifacts.delete(key);
            return new Failed(unit, failStep(step, null, FailureCode.PACKAGE_CHECKSUM_MISMATCH,
                    "Package declared SHA-256 " + fetched.declaredSha256() + " but " + fetched.stored().sha256()
                            + " was stored",
                    false));
        }
        artifactRows.add(new RunArtifact(UUID.randomUUID(), unit.runId(), unit.id(), ArtifactKind.ZIP, key,
                fetched.stored().sha256(), fetched.stored().sizeBytes(), clock.now()));
        saveStep(step.succeed(clock.now(), "STORED"));
        return new Advanced(unit);
    }

    private static FailureCode packageCode(GatewayException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> FailureCode.PACKAGE_UNAVAILABLE;
            case NOT_FOUND -> FailureCode.PACKAGE_GONE;
            case PROTOCOL -> FailureCode.PROTOCOL_ERROR;
            case CONFLICT, REJECTED -> FailureCode.PROTOCOL_ERROR;
        };
    }

    /** {@code <source>/<season>/<runId>/<ordinal>-<first 12 of the unit key>/ingest-<ingestRunId>.zip}. */
    private static String storageKey(PipelineRun run, RunUnit unit) {
        String shortKey = unit.unitKey().length() <= 12 ? unit.unitKey() : unit.unitKey().substring(0, 12);
        return run.source().name().toLowerCase(Locale.ROOT) + "/" + run.season() + "/" + run.id() + "/"
                + unit.ordinal() + "-" + shortKey + "/ingest-" + unit.ingestRunId() + ".zip";
    }

    // ---------------------------------------------------------------- IMPORT

    private RunUnit importFromPacked(PipelineRun run, RunUnit start, RunArtifact zip) throws InterruptedException {
        RunUnit unit = start;
        List<PipelineStep> attempts = stepsOf(unit, StepKind.IMPORT);
        PipelineStep latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Outcome pending = null;
        if (latest != null) {
            if (latest.status() == StepStatus.RUNNING) {
                if (latest.externalRef() != null) {
                    UUID jobId = UUID.fromString(latest.externalRef());
                    RunUnit importing = reportStep(updateUnit(unit.startImport(jobId, clock.now())), StepKind.IMPORT);
                    pending = pollImport(importing, latest, deadline(unit, StepKind.IMPORT));
                } else {
                    pending = new Failed(unit, interrupted(latest));
                }
            } else if (latest.status() == StepStatus.FAILED) {
                pending = new Failed(unit, latest);
            }
        }
        while (true) {
            Outcome outcome = pending != null ? pending : importAttempt(run, unit, zip);
            pending = null;
            if (outcome instanceof Advanced advanced) {
                return advanced.unit();
            }
            Failed failed = (Failed) outcome;
            unit = failed.unit();
            if (unit.status() == UnitStatus.IMPORTING) {
                return failUnit(unit, failed.step().error());
            }
            Optional<Duration> wait = retryWait(unit, failed.step(), StepKind.IMPORT);
            if (wait.isEmpty()) {
                return failUnit(unit, failed.step().error());
            }
            clock.sleep(wait.get());
        }
    }

    private Outcome importAttempt(PipelineRun run, RunUnit start, RunArtifact zip) throws InterruptedException {
        RunUnit unit = start;
        int attempt = nextAttempt(stepsOf(unit, StepKind.IMPORT));
        PipelineStep step = saveStep(PipelineStep.start(
                UUID.randomUUID(), unit.runId(), unit.id(), StepKind.IMPORT, attempt, clock.now(), null));
        Instant deadline = deadline(unit, StepKind.IMPORT);
        ImportSubmission submission;
        try {
            ArtifactContent content = artifacts.content(zip.storageKey());
            submission = importGateway.submit(run.id() + "-" + unit.ordinal() + ".zip", content, run.id());
        } catch (GatewayException e) {
            return new Failed(unit, failStep(step, null, importStartCode(e), e.getMessage(),
                    e.kind() == GatewayException.Kind.UNAVAILABLE));
        } catch (ArtifactStoreException e) {
            return new Failed(unit, failStep(step, null, FailureCode.ARTIFACT_STORE_FAILED, e.getMessage(), false));
        }
        step = saveStep(step.withImportJob(submission.importJobId(), !submission.created()));
        unit = reportStep(updateUnit(unit.startImport(submission.importJobId(), clock.now())), StepKind.IMPORT);
        return pollImport(unit, step, deadline);
    }

    private static FailureCode importStartCode(GatewayException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> FailureCode.PLATFORM_UNAVAILABLE;
            case REJECTED, NOT_FOUND -> FailureCode.IMPORT_REJECTED;
            case CONFLICT -> FailureCode.IMPORT_SHRINK;
            case PROTOCOL -> FailureCode.PROTOCOL_ERROR;
        };
    }

    private RunUnit importingPhase(PipelineRun run, RunUnit start) throws InterruptedException {
        RunUnit unit = reportStep(start, StepKind.IMPORT);
        List<PipelineStep> attempts = stepsOf(unit, StepKind.IMPORT);
        if (attempts.isEmpty()) {
            throw new IllegalStateException("Unit " + unit.id() + " is IMPORTING but has no IMPORT step");
        }
        PipelineStep latest = attempts.get(attempts.size() - 1);
        Outcome outcome = switch (latest.status()) {
            case RUNNING -> pollImport(unit, latest, deadline(unit, StepKind.IMPORT));
            case SUCCEEDED -> new Advanced("PARTIAL".equals(latest.outcome())
                    ? updateUnit(unit.partial(clock.now()))
                    : updateUnit(unit.succeed(clock.now())));
            case FAILED -> new Failed(unit, latest);
        };
        if (outcome instanceof Advanced advanced) {
            return advanced.unit();
        }
        Failed failed = (Failed) outcome;
        return failUnit(failed.unit(), failed.step().error());
    }

    private Outcome pollImport(RunUnit unit, PipelineStep step, Instant deadline) throws InterruptedException {
        UUID jobId = unit.importJobId();
        int consecutiveFailures = 0;
        while (true) {
            if (!clock.now().isBefore(deadline)) {
                return new Failed(unit, failStep(step, null, FailureCode.STEP_TIMEOUT,
                        "Import job " + jobId + " did not finish before the step timeout", false));
            }
            ImportJobState job;
            try {
                job = importGateway.getJob(jobId);
                consecutiveFailures = 0;
            } catch (GatewayException e) {
                switch (e.kind()) {
                    case NOT_FOUND -> {
                        return new Failed(unit, failStep(step, null, FailureCode.IMPORT_JOB_LOST,
                                "Import job " + jobId + " is unknown to the platform", false));
                    }
                    case UNAVAILABLE -> {
                        consecutiveFailures++;
                        Optional<Duration> wait = settings.retry().delayBeforeRetry(consecutiveFailures - 1);
                        if (wait.isEmpty()) {
                            return new Failed(unit, failStep(step, null, FailureCode.PLATFORM_UNAVAILABLE,
                                    consecutiveFailures + " consecutive polls failed: " + e.getMessage(), true));
                        }
                        sleepUntil(wait.get(), deadline);
                        continue;
                    }
                    default -> {
                        return new Failed(unit, failStep(step, null, FailureCode.PROTOCOL_ERROR, e.getMessage(),
                                false));
                    }
                }
            }
            if (job.finished()) {
                return finishImport(unit, step, job);
            }
            sleepUntil(settings.polls().importJob(), deadline);
        }
    }

    private Outcome finishImport(RunUnit unit, PipelineStep step, ImportJobState job) {
        if (reports.findByUnitId(unit.id()).isEmpty()) {
            reports.add(ImportReportMapper.toReport(unit.runId(), unit.id(), job, clock.now()));
        }
        switch (job.status()) {
            case "SUCCEEDED" -> {
                saveStep(step.succeed(clock.now(), "SUCCEEDED"));
                return new Advanced(updateUnit(unit.succeed(clock.now())));
            }
            case "PARTIAL" -> {
                saveStep(step.succeed(clock.now(), "PARTIAL"));
                return new Advanced(updateUnit(unit.partial(clock.now())));
            }
            default -> {
                return new Failed(unit, failStep(step, "FAILED", FailureCode.IMPORT_FAILED,
                        orDefault(job.errorDetail(), "Import job " + job.importJobId() + " failed"), false));
            }
        }
    }

    // --------------------------------------------------------------- helpers

    /** The wait before the next attempt, or empty when the failure is final or the wait would cross the deadline. */
    private Optional<Duration> retryWait(RunUnit unit, PipelineStep failed, StepKind kind) {
        if (!Boolean.TRUE.equals(failed.retryable())) {
            return Optional.empty();
        }
        Optional<Duration> delay = settings.retry().delayBeforeRetry(failed.attempt() - 1);
        if (delay.isEmpty()) {
            return Optional.empty();
        }
        Instant deadline = deadline(unit, kind);
        if (!clock.now().plus(delay.get()).isBefore(deadline)) {
            return Optional.empty();
        }
        return delay;
    }

    /** The kind's deadline for the unit: its first attempt's start plus the configured timeout. */
    private Instant deadline(RunUnit unit, StepKind kind) {
        Instant first = stepsOf(unit, kind).stream()
                .map(PipelineStep::startedAt)
                .min(Comparator.naturalOrder())
                .orElseGet(clock::now);
        return first.plus(settings.timeouts().of(kind));
    }

    private void sleepUntil(Duration wait, Instant deadline) throws InterruptedException {
        Duration remaining = Duration.between(clock.now(), deadline);
        Duration clipped = wait.compareTo(remaining) < 0 ? wait : remaining;
        if (!clipped.isNegative() && !clipped.isZero()) {
            clock.sleep(clipped);
        }
    }

    private List<PipelineStep> stepsOf(RunUnit unit, StepKind kind) {
        return steps.findByRunId(unit.runId()).stream()
                .filter(step -> step.unitId().equals(unit.id()) && step.kind() == kind)
                .sorted(Comparator.comparingInt(PipelineStep::attempt))
                .toList();
    }

    private static int nextAttempt(List<PipelineStep> attempts) {
        return attempts.stream().mapToInt(PipelineStep::attempt).max().orElse(0) + 1;
    }

    private PipelineStep interrupted(PipelineStep running) {
        return failStep(running, null, FailureCode.INTERRUPTED,
                "The " + running.kind() + " attempt " + running.attempt() + " was interrupted by a restart", true);
    }

    private PipelineStep failStep(
            PipelineStep step, String outcome, FailureCode code, String message, boolean retryable) {
        return failStep(step, outcome, null, code, message, retryable);
    }

    private PipelineStep failStep(
            PipelineStep step,
            String outcome,
            IngestHealth health,
            FailureCode code,
            String message,
            boolean retryable) {
        RunError error = new RunError(code.name(), orDefault(message, code.name()));
        return saveStep(step.fail(clock.now(), outcome, error, retryable, health));
    }

    private RunUnit failUnit(RunUnit unit, RunError error) {
        return updateUnit(unit.fail(error, clock.now()));
    }

    private RunUnit updateUnit(RunUnit unit) {
        RunUnit saved = units.update(unit);
        observer.unitChanged(saved);
        return saved;
    }

    private PipelineStep saveStep(PipelineStep step) {
        PipelineStep saved = steps.save(step);
        observer.stepChanged(saved);
        return saved;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
