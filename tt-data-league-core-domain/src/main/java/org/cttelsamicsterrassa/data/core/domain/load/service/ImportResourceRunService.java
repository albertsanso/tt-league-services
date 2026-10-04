package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one registered import run of an import resource to its terminal outcome on the calling thread: marks the
 * run running, processes the resource with progress updates, finishes the resource and completes the run in
 * {@link ImportRunRegistry}. Shared by the manual start command and the import jobs.
 */
@Named
public class ImportResourceRunService {
    private static final Logger LOGGER = Logger.getLogger(ImportResourceRunService.class.getName());

    private final ImportResourceRepository repository;
    private final ImportResourceProcessService service;
    private final ImportRunRegistry runRegistry;
    private final Clock clock;

    @Inject
    public ImportResourceRunService(ImportResourceRepository repository,
                                    ImportResourceProcessService service,
                                    ImportRunRegistry runRegistry) {
        this(repository, service, runRegistry, Clock.systemDefaultZone());
    }

    public ImportResourceRunService(ImportResourceRepository repository,
                                    ImportResourceProcessService service,
                                    ImportRunRegistry runRegistry,
                                    Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.service = Objects.requireNonNull(service, "service");
        this.runRegistry = Objects.requireNonNull(runRegistry, "runRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Moves the resource to {@code PROCESSING} (through {@code PENDING} when it was finished) and saves it.
     *
     * @throws IllegalStateException when the resource is already {@code PROCESSING}
     */
    public void markProcessing(ImportResource resource) {
        resource.setPending();
        resource.startProcessing();
        repository.save(resource);
    }

    /**
     * Processes the resource of the registered run {@code runId} and returns the run's terminal snapshot.
     * The resource must already be {@code PROCESSING} (see {@link #markProcessing(ImportResource)}).
     *
     * @throws IllegalStateException when the registry no longer knows the run
     */
    public ImportRunSnapshot run(UUID runId, ImportResource resource) {
        runRegistry.markRunning(runId, ImportRunProgress.zero());
        try {
            ImportProcessResult result = service.process(resource,
                    progress -> runRegistry.updateProgress(runId, progress));
            resource.finishProcessing(result.status() == ImportProcessStatus.SUCCESS, ZonedDateTime.now(clock));
            repository.save(resource);
            runRegistry.complete(runId, ImportRunStatus.fromProcessStatus(result.status()),
                    progressFrom(result), result, null);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Unexpected failure while processing import resource " + resource.getId(),
                    exception);
            resource.finishProcessing(false, ZonedDateTime.now(clock));
            repository.save(resource);
            runRegistry.complete(runId, ImportRunStatus.FAILURE,
                    ImportRunProgress.indeterminate(0, 0, 1), null, safeMessage(exception));
        }
        return runRegistry.findByRunId(runId)
                .orElseThrow(() -> new IllegalStateException("Import run registry lost run " + runId));
    }

    private static ImportRunProgress progressFrom(ImportProcessResult result) {
        long processed = result.itemsPersisted() + result.skipped();
        long total = result.filesSeen();
        return total > 0
                ? ImportRunProgress.determinate(processed, total, result.skipped(), result.processorFailures())
                : ImportRunProgress.indeterminate(processed, result.skipped(), result.processorFailures());
    }

    public static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
