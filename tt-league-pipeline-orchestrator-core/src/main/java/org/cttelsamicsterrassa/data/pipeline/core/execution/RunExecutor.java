package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;

/**
 * Drives one run through INGEST, FETCH_PACKAGE and IMPORT. {@link #execute(UUID)} is the only entry point and serves
 * fresh and resumed runs: it continues from whatever status and step rows are stored.
 */
public final class RunExecutor {

    private static final System.Logger LOG = System.getLogger(RunExecutor.class.getName());
    private static final int MAX_INTERNAL_MESSAGE = 500;

    private final PipelineRunRepository runs;
    private final PipelineStepRepository steps;
    private final RunArtifactRepository artifactRows;
    private final ImportReportRepository reports;
    private final IngestGateway ingest;
    private final ImportGateway importGateway;
    private final ArtifactStore artifacts;
    private final RunClock clock;
    private final RunObserver observer;
    private final ExecutionSettings settings;

    public RunExecutor(
            PipelineRunRepository runs,
            PipelineStepRepository steps,
            RunArtifactRepository artifactRows,
            ImportReportRepository reports,
            IngestGateway ingest,
            ImportGateway importGateway,
            ArtifactStore artifacts,
            RunClock clock,
            RunObserver observer,
            ExecutionSettings settings) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
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

    /** Result of one step attempt: the run moved on, or the attempt's step row ended FAILED. */
    private sealed interface Outcome permits Advanced, Failed {
    }

    private record Advanced(PipelineRun run) implements Outcome {
    }

    private record Failed(PipelineRun run, PipelineStep step) implements Outcome {
    }

    public void execute(UUID runId) {
        Optional<PipelineRun> loaded = runs.findById(runId);
        if (loaded.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Run {0} does not exist; nothing to execute", runId);
            return;
        }
        if (loaded.get().status().isTerminal()) {
            return;
        }
        try {
            drive(loaded.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.INFO, "Run {0} interrupted; recovery resumes it", runId);
        } catch (StaleRunException e) {
            LOG.log(System.Logger.Level.WARNING, "Run {0} is owned by another executor; stopping", runId);
        } catch (RuntimeException e) {
            failUnexpected(runId, e);
        }
    }

    private void drive(PipelineRun start) throws InterruptedException {
        PipelineRun run = start;
        while (!run.status().isTerminal()) {
            run = switch (run.status()) {
                case QUEUED, RUNNING_INGEST -> ingestPhase(run);
                case PACKED -> packedPhase(run);
                case IMPORTING -> importingPhase(run);
                default -> throw new IllegalStateException("Run " + run.id() + " is in unexpected status "
                        + run.status());
            };
        }
    }

    // ---------------------------------------------------------------- INGEST

    private PipelineRun ingestPhase(PipelineRun run) throws InterruptedException {
        List<PipelineStep> attempts = stepsOf(run.id(), StepKind.INGEST);
        PipelineStep latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Outcome pending = null;
        if (latest != null) {
            switch (latest.status()) {
                case RUNNING -> {
                    if (run.status() == RunStatus.RUNNING_INGEST && latest.externalRef() != null) {
                        Instant deadline = deadline(run.id(), StepKind.INGEST);
                        pending = pollIngest(run, latest, deadline);
                    } else {
                        pending = new Failed(run, interrupted(latest));
                    }
                }
                case SUCCEEDED -> {
                    return afterIngestSuccess(run, latest);
                }
                case FAILED -> pending = new Failed(run, latest);
            }
        }
        while (true) {
            Outcome outcome = pending != null ? pending : ingestAttempt(run);
            pending = null;
            if (outcome instanceof Advanced advanced) {
                return advanced.run();
            }
            Failed failed = (Failed) outcome;
            run = failed.run();
            Optional<Duration> wait = retryWait(failed.step(), StepKind.INGEST);
            if (wait.isEmpty()) {
                return failRun(run, failed.step().error());
            }
            clock.sleep(wait.get());
        }
    }

    private PipelineRun afterIngestSuccess(PipelineRun run, PipelineStep succeeded) {
        Instant now = clock.now();
        if ("NO_CHANGES".equals(succeeded.outcome())) {
            return updateRun(run.noChanges(now));
        }
        return updateRun(run.packed(now));
    }

    private Outcome ingestAttempt(PipelineRun run) throws InterruptedException {
        List<PipelineStep> previous = stepsOf(run.id(), StepKind.INGEST);
        int attempt = nextAttempt(previous);
        PipelineStep step = saveStep(
                PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, attempt, clock.now(), null));
        Instant deadline = deadline(run.id(), StepKind.INGEST);

        String ingestRunId;
        try {
            ingestRunId = ingest.startRun(IngestRunRequest.forRun(run));
        } catch (GatewayException e) {
            return new Failed(run, failStep(step, null, ingestStartCode(e), e.getMessage(),
                    e.kind() == GatewayException.Kind.UNAVAILABLE));
        }
        step = saveStep(step.withExternalRef(ingestRunId));
        Instant now = clock.now();
        run = updateRun(run.status() == RunStatus.QUEUED
                ? run.startIngest(ingestRunId, now)
                : run.restartIngest(ingestRunId, now));
        return pollIngest(run, step, deadline);
    }

    private static FailureCode ingestStartCode(GatewayException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> FailureCode.INGEST_UNAVAILABLE;
            case CONFLICT -> FailureCode.INGEST_BUSY;
            case REJECTED, NOT_FOUND -> FailureCode.INGEST_REJECTED;
            case PROTOCOL -> FailureCode.PROTOCOL_ERROR;
        };
    }

    private Outcome pollIngest(PipelineRun run, PipelineStep step, Instant deadline) throws InterruptedException {
        String ingestRunId = run.ingestRunId();
        int consecutiveFailures = 0;
        while (true) {
            if (!clock.now().isBefore(deadline)) {
                return new Failed(run, failStep(step, null, FailureCode.STEP_TIMEOUT,
                        "Ingest run " + ingestRunId + " did not finish before the step timeout", false));
            }
            IngestRunState state;
            try {
                state = ingest.getRun(ingestRunId);
                consecutiveFailures = 0;
            } catch (GatewayException e) {
                switch (e.kind()) {
                    case NOT_FOUND -> {
                        return new Failed(run, failStep(step, null, FailureCode.INGEST_RUN_LOST,
                                "Ingest run " + ingestRunId + " is unknown to the ingest service (restarted?)",
                                true));
                    }
                    case UNAVAILABLE -> {
                        consecutiveFailures++;
                        Optional<Duration> wait = settings.retry().delayBeforeRetry(consecutiveFailures - 1);
                        if (wait.isEmpty()) {
                            return new Failed(run, failStep(step, null, FailureCode.INGEST_UNAVAILABLE,
                                    consecutiveFailures + " consecutive polls failed: " + e.getMessage(), true));
                        }
                        sleepUntil(wait.get(), deadline);
                        continue;
                    }
                    default -> {
                        return new Failed(run, failStep(step, null, FailureCode.PROTOCOL_ERROR, e.getMessage(),
                                false));
                    }
                }
            }
            if (state.finished()) {
                return finishIngest(run, step, state);
            }
            sleepUntil(settings.polls().ingest(), deadline);
        }
    }

    private Outcome finishIngest(PipelineRun run, PipelineStep step, IngestRunState state) {
        String outcome = state.outcome();
        switch (outcome) {
            case "NO_CHANGES" -> {
                saveStep(step.succeed(clock.now(), "NO_CHANGES"));
                return new Advanced(updateRun(run.noChanges(clock.now())));
            }
            case "SUCCEEDED", "COMPLETED_WITH_ISSUES" -> {
                if (!state.packageAvailable()) {
                    return new Failed(run, failStep(step, outcome, FailureCode.INGEST_NO_PACKAGE,
                            "Ingest run " + state.ingestRunId() + " finished " + outcome + " without a package",
                            false));
                }
                saveStep(step.succeed(clock.now(), outcome));
                return new Advanced(updateRun(run.packed(clock.now())));
            }
            case "SOURCE_UNAVAILABLE" -> {
                return new Failed(run, failStep(step, outcome, FailureCode.SOURCE_UNAVAILABLE,
                        orDefault(state.error(), "The source was unavailable for ingest run " + state.ingestRunId()),
                        true));
            }
            case "FAILED" -> {
                return new Failed(run, failStep(step, outcome, FailureCode.INGEST_FAILED,
                        orDefault(state.error(), "Ingest run " + state.ingestRunId() + " failed"), false));
            }
            default -> {
                return new Failed(run, failStep(step, null, FailureCode.PROTOCOL_ERROR,
                        "Unexpected ingest outcome: " + truncate(outcome, 100), false));
            }
        }
    }

    // --------------------------------------------------------- FETCH_PACKAGE

    private PipelineRun packedPhase(PipelineRun run) throws InterruptedException {
        Optional<RunArtifact> zip = artifactRows.findByRunId(run.id()).stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                .findFirst();
        if (zip.isEmpty()) {
            return fetchPackage(run);
        }
        return importFromPacked(run, zip.get());
    }

    private PipelineRun fetchPackage(PipelineRun run) throws InterruptedException {
        List<PipelineStep> attempts = stepsOf(run.id(), StepKind.FETCH_PACKAGE);
        PipelineStep latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Outcome pending = null;
        if (latest != null) {
            if (latest.status() == StepStatus.RUNNING) {
                pending = new Failed(run, interrupted(latest));
            } else if (latest.status() == StepStatus.FAILED) {
                pending = new Failed(run, latest);
            }
        }
        while (true) {
            Outcome outcome = pending != null ? pending : fetchAttempt(run);
            pending = null;
            if (outcome instanceof Advanced advanced) {
                return advanced.run();
            }
            Failed failed = (Failed) outcome;
            Optional<Duration> wait = retryWait(failed.step(), StepKind.FETCH_PACKAGE);
            if (wait.isEmpty()) {
                return failRun(failed.run(), failed.step().error());
            }
            clock.sleep(wait.get());
        }
    }

    private Outcome fetchAttempt(PipelineRun run) {
        String key = storageKey(run);
        int attempt = nextAttempt(stepsOf(run.id(), StepKind.FETCH_PACKAGE));
        PipelineStep step = saveStep(PipelineStep.start(
                UUID.randomUUID(), run.id(), StepKind.FETCH_PACKAGE, attempt, clock.now(), run.ingestRunId()));
        FetchedPackage fetched;
        try {
            artifacts.delete(key);
            fetched = ingest.fetchPackage(run.ingestRunId(), body -> artifacts.store(key, body));
        } catch (GatewayException e) {
            return new Failed(run, failStep(step, null, packageCode(e), e.getMessage(),
                    e.kind() == GatewayException.Kind.UNAVAILABLE));
        } catch (ArtifactStoreException e) {
            return new Failed(run, failStep(step, null, FailureCode.ARTIFACT_STORE_FAILED, e.getMessage(), false));
        }
        if (!fetched.stored().sha256().equals(fetched.declaredSha256())) {
            artifacts.delete(key);
            return new Failed(run, failStep(step, null, FailureCode.PACKAGE_CHECKSUM_MISMATCH,
                    "Package declared SHA-256 " + fetched.declaredSha256() + " but " + fetched.stored().sha256()
                            + " was stored",
                    false));
        }
        artifactRows.add(new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.ZIP, key,
                fetched.stored().sha256(), fetched.stored().sizeBytes(), clock.now()));
        saveStep(step.succeed(clock.now(), "STORED"));
        return new Advanced(run);
    }

    private static FailureCode packageCode(GatewayException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> FailureCode.PACKAGE_UNAVAILABLE;
            case NOT_FOUND -> FailureCode.PACKAGE_GONE;
            case PROTOCOL -> FailureCode.PROTOCOL_ERROR;
            case CONFLICT, REJECTED -> FailureCode.PROTOCOL_ERROR;
        };
    }

    private static String storageKey(PipelineRun run) {
        return run.source().name().toLowerCase(Locale.ROOT) + "/" + run.season() + "/" + run.id() + "/ingest-"
                + run.ingestRunId() + ".zip";
    }

    // ---------------------------------------------------------------- IMPORT

    private PipelineRun importFromPacked(PipelineRun run, RunArtifact zip) throws InterruptedException {
        List<PipelineStep> attempts = stepsOf(run.id(), StepKind.IMPORT);
        PipelineStep latest = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        Outcome pending = null;
        if (latest != null) {
            if (latest.status() == StepStatus.RUNNING) {
                if (latest.externalRef() != null) {
                    UUID jobId = UUID.fromString(latest.externalRef());
                    PipelineRun importing = updateRun(run.startImport(jobId, clock.now()));
                    pending = pollImport(importing, latest, deadline(run.id(), StepKind.IMPORT));
                } else {
                    pending = new Failed(run, interrupted(latest));
                }
            } else if (latest.status() == StepStatus.FAILED) {
                pending = new Failed(run, latest);
            }
        }
        while (true) {
            Outcome outcome = pending != null ? pending : importAttempt(run, zip);
            pending = null;
            if (outcome instanceof Advanced advanced) {
                return advanced.run();
            }
            Failed failed = (Failed) outcome;
            run = failed.run();
            if (run.status() == RunStatus.IMPORTING) {
                return failRun(run, failed.step().error());
            }
            Optional<Duration> wait = retryWait(failed.step(), StepKind.IMPORT);
            if (wait.isEmpty()) {
                return failRun(run, failed.step().error());
            }
            clock.sleep(wait.get());
        }
    }

    private Outcome importAttempt(PipelineRun run, RunArtifact zip) throws InterruptedException {
        int attempt = nextAttempt(stepsOf(run.id(), StepKind.IMPORT));
        PipelineStep step = saveStep(
                PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.IMPORT, attempt, clock.now(), null));
        Instant deadline = deadline(run.id(), StepKind.IMPORT);
        ImportSubmission submission;
        try {
            ArtifactContent content = artifacts.content(zip.storageKey());
            submission = importGateway.submit(run.id() + ".zip", content, run.id());
        } catch (GatewayException e) {
            return new Failed(run, failStep(step, null, importStartCode(e), e.getMessage(),
                    e.kind() == GatewayException.Kind.UNAVAILABLE));
        } catch (ArtifactStoreException e) {
            return new Failed(run, failStep(step, null, FailureCode.ARTIFACT_STORE_FAILED, e.getMessage(), false));
        }
        step = saveStep(step.withExternalRef(submission.importJobId().toString()));
        run = updateRun(run.startImport(submission.importJobId(), clock.now()));
        return pollImport(run, step, deadline);
    }

    private static FailureCode importStartCode(GatewayException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> FailureCode.PLATFORM_UNAVAILABLE;
            case REJECTED, NOT_FOUND -> FailureCode.IMPORT_REJECTED;
            case CONFLICT -> FailureCode.IMPORT_SHRINK;
            case PROTOCOL -> FailureCode.PROTOCOL_ERROR;
        };
    }

    private PipelineRun importingPhase(PipelineRun run) throws InterruptedException {
        List<PipelineStep> attempts = stepsOf(run.id(), StepKind.IMPORT);
        if (attempts.isEmpty()) {
            throw new IllegalStateException("Run " + run.id() + " is IMPORTING but has no IMPORT step");
        }
        PipelineStep latest = attempts.get(attempts.size() - 1);
        Outcome outcome = switch (latest.status()) {
            case RUNNING -> pollImport(run, latest, deadline(run.id(), StepKind.IMPORT));
            case SUCCEEDED -> new Advanced("PARTIAL".equals(latest.outcome())
                    ? updateRun(run.partial(clock.now()))
                    : updateRun(run.succeed(clock.now())));
            case FAILED -> new Failed(run, latest);
        };
        if (outcome instanceof Advanced advanced) {
            return advanced.run();
        }
        Failed failed = (Failed) outcome;
        return failRun(failed.run(), failed.step().error());
    }

    private Outcome pollImport(PipelineRun run, PipelineStep step, Instant deadline) throws InterruptedException {
        UUID jobId = run.importJobId();
        int consecutiveFailures = 0;
        while (true) {
            if (!clock.now().isBefore(deadline)) {
                return new Failed(run, failStep(step, null, FailureCode.STEP_TIMEOUT,
                        "Import job " + jobId + " did not finish before the step timeout", false));
            }
            ImportJobState job;
            try {
                job = importGateway.getJob(jobId);
                consecutiveFailures = 0;
            } catch (GatewayException e) {
                switch (e.kind()) {
                    case NOT_FOUND -> {
                        return new Failed(run, failStep(step, null, FailureCode.IMPORT_JOB_LOST,
                                "Import job " + jobId + " is unknown to the platform", false));
                    }
                    case UNAVAILABLE -> {
                        consecutiveFailures++;
                        Optional<Duration> wait = settings.retry().delayBeforeRetry(consecutiveFailures - 1);
                        if (wait.isEmpty()) {
                            return new Failed(run, failStep(step, null, FailureCode.PLATFORM_UNAVAILABLE,
                                    consecutiveFailures + " consecutive polls failed: " + e.getMessage(), true));
                        }
                        sleepUntil(wait.get(), deadline);
                        continue;
                    }
                    default -> {
                        return new Failed(run, failStep(step, null, FailureCode.PROTOCOL_ERROR, e.getMessage(),
                                false));
                    }
                }
            }
            if (job.finished()) {
                return finishImport(run, step, job);
            }
            sleepUntil(settings.polls().importJob(), deadline);
        }
    }

    private Outcome finishImport(PipelineRun run, PipelineStep step, ImportJobState job) {
        if (reports.findByRunId(run.id()).isEmpty()) {
            reports.add(ImportReportMapper.toReport(run.id(), job, clock.now()));
        }
        switch (job.status()) {
            case "SUCCEEDED" -> {
                saveStep(step.succeed(clock.now(), "SUCCEEDED"));
                return new Advanced(updateRun(run.succeed(clock.now())));
            }
            case "PARTIAL" -> {
                saveStep(step.succeed(clock.now(), "PARTIAL"));
                return new Advanced(updateRun(run.partial(clock.now())));
            }
            default -> {
                return new Failed(run, failStep(step, "FAILED", FailureCode.IMPORT_FAILED,
                        orDefault(job.errorDetail(), "Import job " + job.importJobId() + " failed"), false));
            }
        }
    }

    // --------------------------------------------------------------- helpers

    /** The wait before the next attempt, or empty when the failure is final or the wait would cross the deadline. */
    private Optional<Duration> retryWait(PipelineStep failed, StepKind kind) {
        if (!Boolean.TRUE.equals(failed.retryable())) {
            return Optional.empty();
        }
        Optional<Duration> delay = settings.retry().delayBeforeRetry(failed.attempt() - 1);
        if (delay.isEmpty()) {
            return Optional.empty();
        }
        Instant deadline = deadline(failed.runId(), kind);
        if (!clock.now().plus(delay.get()).isBefore(deadline)) {
            return Optional.empty();
        }
        return delay;
    }

    /** The kind's deadline: its first attempt's start plus the configured timeout. */
    private Instant deadline(UUID runId, StepKind kind) {
        Instant first = stepsOf(runId, kind).stream()
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

    private List<PipelineStep> stepsOf(UUID runId, StepKind kind) {
        return steps.findByRunId(runId).stream()
                .filter(step -> step.kind() == kind)
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
        RunError error = new RunError(code.name(), orDefault(message, code.name()));
        return saveStep(step.fail(clock.now(), outcome, error, retryable));
    }

    private PipelineRun failRun(PipelineRun run, RunError error) {
        return updateRun(run.fail(error, clock.now()));
    }

    private PipelineRun updateRun(PipelineRun run) {
        PipelineRun saved = runs.update(run);
        observer.runChanged(saved);
        return saved;
    }

    private PipelineStep saveStep(PipelineStep step) {
        PipelineStep saved = steps.save(step);
        observer.stepChanged(saved);
        return saved;
    }

    private void failUnexpected(UUID runId, RuntimeException cause) {
        PipelineRun run = runs.findById(runId).orElseThrow(() -> cause);
        LOG.log(System.Logger.Level.ERROR, "Run " + runId + " failed unexpectedly", cause);
        if (run.status().isTerminal()) {
            return;
        }
        String message = truncate(cause.getClass().getName() + ": " + cause.getMessage(), MAX_INTERNAL_MESSAGE);
        for (PipelineStep step : steps.findByRunId(runId)) {
            if (step.status() == StepStatus.RUNNING) {
                failStep(step, null, FailureCode.INTERNAL_ERROR, message, false);
            }
        }
        failRun(run, new RunError(FailureCode.INTERNAL_ERROR.name(), message));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
