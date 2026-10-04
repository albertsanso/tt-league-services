package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceRunService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceRepositoryLoaderService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceUploadService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceZipService;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Submits uploaded ZIPs as {@link ImportJob}s and executes them: stores the ZIP content in the import folder and
 * imports every ACTAS season of its manifest through {@link ImportResourceRunService}, one season at a time.
 *
 * <p>Jobs are executed one at a time by the runtime's {@link ImportJobDispatcher}. Imports are single-run
 * system-wide, so a job waits (bounded by {@link ImportJobSettings}) while another import, such as a manually
 * started one, is active. Submissions are serialized in this JVM so that two identical uploads cannot both create a
 * job.</p>
 *
 * <p>Not component-scanned: the API runtime declares it as a bean, because it needs the runtime's
 * {@link ImportJobDispatcher} and {@link ImportJobSettings}, and the import runtime scans every package.</p>
 */
public class ImportJobService {
    private static final Logger LOGGER = Logger.getLogger(ImportJobService.class.getName());

    static final String STAGING_FOLDER = "import-jobs";
    static final String INTERRUPTED_REASON = "Interrupted by a platform restart";
    private static final Pattern CLIENT_RUN_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Set<ImportJobStatus> IN_FLIGHT = Set.of(ImportJobStatus.STORING, ImportJobStatus.IMPORTING);

    private final ResourceUploadService uploadService;
    private final ResourceZipService zipService;
    private final ResourceRepositoryLoaderService loaderService;
    private final ImportRunRegistry runRegistry;
    private final ImportResourceRunService runService;
    private final ImportResourceRepository importResourceRepository;
    private final ImportJobRepository jobRepository;
    private final ImportJobDispatcher dispatcher;
    private final ImportJobSettings settings;
    private final Clock clock;
    private final Sleeper sleeper;

    public ImportJobService(ResourceUploadService uploadService,
                            ResourceZipService zipService,
                            ResourceRepositoryLoaderService loaderService,
                            ImportRunRegistry runRegistry,
                            ImportResourceRunService runService,
                            ImportResourceRepository importResourceRepository,
                            ImportJobRepository jobRepository,
                            ImportJobDispatcher dispatcher,
                            ImportJobSettings settings) {
        this(uploadService, zipService, loaderService, runRegistry, runService, importResourceRepository,
                jobRepository, dispatcher, settings, Clock.systemDefaultZone(), Sleeper.THREAD);
    }

    public ImportJobService(ResourceUploadService uploadService,
                            ResourceZipService zipService,
                            ResourceRepositoryLoaderService loaderService,
                            ImportRunRegistry runRegistry,
                            ImportResourceRunService runService,
                            ImportResourceRepository importResourceRepository,
                            ImportJobRepository jobRepository,
                            ImportJobDispatcher dispatcher,
                            ImportJobSettings settings,
                            Clock clock,
                            Sleeper sleeper) {
        this.uploadService = Objects.requireNonNull(uploadService, "uploadService");
        this.zipService = Objects.requireNonNull(zipService, "zipService");
        this.loaderService = Objects.requireNonNull(loaderService, "loaderService");
        this.runRegistry = Objects.requireNonNull(runRegistry, "runRegistry");
        this.runService = Objects.requireNonNull(runService, "runService");
        this.importResourceRepository = Objects.requireNonNull(importResourceRepository, "importResourceRepository");
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * Validates an upload and creates a {@code QUEUED} job for it, or returns the existing job for the same
     * content. When the manifest declares {@code contentSha256} and a job of the same source and hash is active or
     * ended {@code SUCCEEDED}/{@code PARTIAL}, that job is returned and nothing is staged; the deduplication check
     * runs before the published-acta shrink check. Without {@code contentSha256} there is no deduplication.
     *
     * @throws IllegalArgumentException when the file, ZIP, manifest or {@code clientRunId} is invalid
     * @throws org.cttelsamicsterrassa.data.core.domain.load.service.SnapshotShrinkException when the upload shrinks
     *         published actas and {@code allowPublishedShrink} is false
     */
    public synchronized SubmitResult submit(String filename, byte[] content, Optional<String> clientRunId,
                                            boolean allowPublishedShrink, String requestedBy) {
        Objects.requireNonNull(clientRunId, "clientRunId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        clientRunId.ifPresent(ImportJobService::requireValidClientRunId);
        ImportManifest manifest = uploadService.readManifest(filename, content);
        try {
            ImportSource source = ImportSource.valueOf(manifest.source());
            Optional<String> contentSha256 = manifest.provenance().contentSha256();
            if (contentSha256.isPresent()) {
                Optional<ImportJob> existing =
                        jobRepository.findActiveOrSucceededBySourceAndContentSha256(source, contentSha256.get());
                if (existing.isPresent()) {
                    return new SubmitResult(existing.get(), false);
                }
            }
            loaderService.verifyPublishedActasNotShrinking(manifest, allowPublishedShrink);

            UUID jobId = UUID.randomUUID();
            Path stagedZip = stage(jobId, content);
            ImportJob job = ImportJob.queued(jobId, source, manifest.seasons(), manifest.mode(), contentSha256,
                    clientRunId, manifest.provenance().runId(), allowPublishedShrink, stagedZip, requestedBy, now());
            jobRepository.save(job);
            try {
                dispatcher.dispatch(jobId);
            } catch (RuntimeException dispatchFailure) {
                job.fail("Unable to dispatch the import job: " + ImportResourceRunService.safeMessage(
                        dispatchFailure), now());
                jobRepository.save(job);
                deleteStagedZip(job);
                throw dispatchFailure;
            }
            return new SubmitResult(job, true);
        } finally {
            zipService.deleteExtractionFolder(manifest);
        }
    }

    /**
     * Executes a {@code QUEUED} job to a terminal status. A job in any other status is left untouched. The
     * extraction folder is always deleted, and the staged ZIP once the job is terminal. A thread interrupt (runtime
     * shutdown) while the job still waits to start storing leaves it {@code QUEUED}, so it resumes after a restart.
     */
    public void execute(UUID jobId) {
        ImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown import job " + jobId));
        if (job.getStatus() != ImportJobStatus.QUEUED) {
            LOGGER.log(Level.WARNING, "Import job {0} is {1}, not QUEUED; not executing it",
                    new Object[]{jobId, job.getStatus()});
            return;
        }
        ImportManifest manifest = null;
        try {
            if (!awaitUntil(() -> !runRegistry.hasActiveRun())) {
                failJob(job, busyTimeoutReason());
                return;
            }
            job.startStoring(now());
            jobRepository.save(job);
            manifest = uploadService.readManifest(job.getStagedZipPath().getFileName().toString(),
                    Files.readAllBytes(job.getStagedZipPath()));
            loaderService.verifyPublishedActasNotShrinking(manifest, job.isAllowPublishedShrink());
            List<ImportResource> resources = loaderService.loadIntoRepository(manifest);

            job.startImporting();
            jobRepository.save(job);
            for (ImportResource resource : resources) {
                ImportJobSeason season = job.addSeason(resource.getSeason().toString(), resource.getId());
                jobRepository.save(job);
                importSeason(job, season, resource);
                jobRepository.save(job);
            }
            job.finishFromSeasons(now());
            jobRepository.save(job);
        } catch (IOException exception) {
            failJob(job, "Unable to read the staged ZIP " + job.getStagedZipPath() + ": " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (job.getStatus() == ImportJobStatus.QUEUED) {
                LOGGER.log(Level.WARNING, "Import job {0} was interrupted before storing; it stays QUEUED and "
                        + "resumes after a restart", jobId);
            } else {
                failJob(job, "Interrupted while waiting for another import to finish");
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Import job " + jobId + " failed", exception);
            failJob(job, ImportResourceRunService.safeMessage(exception));
        } finally {
            if (manifest != null) {
                zipService.deleteExtractionFolder(manifest);
            }
            if (job.getStatus().isTerminal()) {
                deleteStagedZip(job);
            }
        }
    }

    /**
     * Recovers jobs after a restart: every {@code STORING}/{@code IMPORTING} job ends {@code FAILED} with
     * {@value #INTERRUPTED_REASON}, and the import resource of a season it left {@code PROCESSING} returns to
     * {@code ERROR} so the season can be imported again. Then every {@code QUEUED} job is dispatched, oldest first.
     * Only resources recorded on the interrupted jobs' own seasons are touched.
     */
    public void recoverAfterRestart() {
        for (ImportJob job : jobRepository.findByStatusIn(IN_FLIGHT)) {
            for (ImportJobSeason season : job.getSeasonResults()) {
                if (!season.getStatus().isTerminal()) {
                    releaseInterruptedResource(job, season);
                }
            }
            job.fail(INTERRUPTED_REASON, now());
            jobRepository.save(job);
            deleteStagedZip(job);
            LOGGER.log(Level.WARNING, "Import job {0} was interrupted by a restart and ended FAILED", job.getId());
        }
        for (ImportJob job : jobRepository.findByStatusIn(Set.of(ImportJobStatus.QUEUED))) {
            dispatcher.dispatch(job.getId());
        }
    }

    private void releaseInterruptedResource(ImportJob job, ImportJobSeason season) {
        importResourceRepository.findById(season.getImportResourceId())
                .filter(resource -> resource.getStatus() == ImportResourceStatus.PROCESSING)
                .ifPresent(resource -> {
                    resource.finishProcessing(false, now());
                    importResourceRepository.save(resource);
                    LOGGER.log(Level.WARNING, "Import resource {0} left PROCESSING by import job {1} is now ERROR",
                            new Object[]{resource.getId(), job.getId()});
                });
    }

    private void importSeason(ImportJob job, ImportJobSeason season, ImportResource resource)
            throws InterruptedException {
        if (resource.getStatus() == ImportResourceStatus.PROCESSING) {
            job.failSeason(season, "Import resource " + resource.getId() + " is already processing");
            return;
        }
        Optional<ImportRunSnapshot> registered = awaitRegistration(resource);
        if (registered.isEmpty()) {
            job.failSeason(season, busyTimeoutReason());
            return;
        }
        UUID runId = registered.get().runId();
        try {
            runService.markProcessing(resource);
        } catch (RuntimeException exception) {
            runRegistry.complete(runId, ImportRunStatus.FAILURE, ImportRunProgress.indeterminate(0, 0, 1), null,
                    ImportResourceRunService.safeMessage(exception));
            throw exception;
        }
        job.recordSeasonRun(season, runService.run(runId, resource));
    }

    private Optional<ImportRunSnapshot> awaitRegistration(ImportResource resource) throws InterruptedException {
        AtomicReference<Optional<ImportRunSnapshot>> registered = new AtomicReference<>(Optional.empty());
        awaitUntil(() -> {
            registered.set(runRegistry.registerQueued(resource.getId(), resource.getSource(),
                    resource.getSeason().toString()));
            return registered.get().isPresent();
        });
        return registered.get();
    }

    /**
     * Evaluates {@code condition} until it holds, sleeping {@code busyRetryInterval} between attempts, for at most
     * {@code busyTimeout}.
     *
     * @return whether the condition held before the timeout
     */
    private boolean awaitUntil(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = clock.instant().plus(settings.busyTimeout());
        while (!condition.getAsBoolean()) {
            if (!clock.instant().isBefore(deadline)) {
                return false;
            }
            sleeper.sleep(settings.busyRetryInterval());
        }
        return true;
    }

    private String busyTimeoutReason() {
        return "Timed out after " + settings.busyTimeout() + " waiting for another import to finish";
    }

    private void failJob(ImportJob job, String reason) {
        if (job.getStatus().isActive()) {
            job.fail(reason, now());
            jobRepository.save(job);
        }
    }

    private Path stage(UUID jobId, byte[] content) {
        Path stagingFolder = Path.of(zipService.getFolderFromSetting()).resolve(STAGING_FOLDER);
        Path stagedZip = stagingFolder.resolve(jobId + ".zip");
        try {
            Files.createDirectories(stagingFolder);
            Files.write(stagedZip, content);
            return stagedZip;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to stage the uploaded ZIP at " + stagedZip, exception);
        }
    }

    private void deleteStagedZip(ImportJob job) {
        try {
            Files.deleteIfExists(job.getStagedZipPath());
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Unable to delete the staged ZIP " + job.getStagedZipPath()
                    + " of import job " + job.getId(), exception);
        }
    }

    private static void requireValidClientRunId(String clientRunId) {
        if (!CLIENT_RUN_ID.matcher(clientRunId).matches()) {
            throw new IllegalArgumentException("runId must match [A-Za-z0-9._-]{1,64}");
        }
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }
}
