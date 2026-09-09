package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportRunStatusDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accepts an import start request and runs it asynchronously on {@code executor}, returning
 * immediately with a queued run snapshot. Callers poll {@link FindImportRunStatusQueryHandler} for
 * progress and the terminal outcome instead of waiting on this call.
 *
 * <p>Only one import may run at a time, system-wide: a request is rejected when the target
 * resource is already {@link ImportResourceStatus#PROCESSING}, and also when {@code runRegistry}
 * reports another resource's run is currently active (see {@link ImportRunRegistry#registerQueued}).</p>
 */
@Named
public class StartImportProcessCommandHandler extends DomainCommandHandler<StartImportProcessCommand> {
    private static final Logger LOGGER = Logger.getLogger(StartImportProcessCommandHandler.class.getName());

    private final ImportResourceRepository repository;
    private final ImportResourceProcessService service;
    private final ImportRunRegistry runRegistry;
    private final Executor executor;

    @Inject
    public StartImportProcessCommandHandler(ImportResourceRepository repository,
                                            ImportResourceProcessService service,
                                            ImportRunRegistry runRegistry,
                                            Executor executor) {
        this.repository = repository;
        this.service = service;
        this.runRegistry = runRegistry;
        this.executor = executor;
    }
    @Override
    public DomainCommandResponse handle(StartImportProcessCommand command) {
        return repository.findById(command.getImportResourceId()).map(resource -> {
            if (resource.getStatus() == ImportResourceStatus.PROCESSING) {
                return DomainCommandResponse.failResponse(
                        ImportRunStatusDtoMapper.alreadyProcessing(command.getImportResourceId()));
            }
            return runRegistry.registerQueued(resource.getId(), resource.getSource(), resource.getSeason().toString())
                    .map(snapshot -> accept(resource, snapshot))
                    .orElseGet(() -> DomainCommandResponse.failResponse(
                            ImportRunStatusDtoMapper.anotherRunActive(resource.getId())));
        }).orElseGet(() -> DomainCommandResponse.failResponse(
                ImportRunStatusDtoMapper.missingResource(command.getImportResourceId())));
    }

    private DomainCommandResponse accept(ImportResource resource, ImportRunSnapshot snapshot) {
        resource.setPending();
        resource.startProcessing();
        repository.save(resource);
        try {
            executor.execute(() -> runAsync(snapshot.runId(), resource));
        } catch (RuntimeException submissionFailure) {
            ImportRunStatusDto rejected = rejectSubmission(resource, snapshot, submissionFailure);
            return DomainCommandResponse.failResponse(rejected);
        }
        return DomainCommandResponse.successResponse(ImportRunStatusDtoMapper.toDto(snapshot));
    }

    private ImportRunStatusDto rejectSubmission(ImportResource resource, ImportRunSnapshot snapshot,
                                                RuntimeException submissionFailure) {
        resource.finishProcessing(false);
        repository.save(resource);
        ImportRunSnapshot failed = runRegistry.complete(snapshot.runId(), ImportRunStatus.FAILURE,
                        ImportRunProgress.indeterminate(0, 0, 1), null, safeMessage(submissionFailure))
                .orElse(snapshot);
        return ImportRunStatusDtoMapper.toDto(failed);
    }

    private void runAsync(UUID runId, ImportResource resource) {
        runRegistry.markRunning(runId, ImportRunProgress.zero());
        try {
            ImportProcessResult result = service.process(resource,
                    progress -> runRegistry.updateProgress(runId, progress));
            resource.finishProcessing(result.status() == ImportProcessStatus.SUCCESS);
            repository.save(resource);
            runRegistry.complete(runId, ImportRunStatus.fromProcessStatus(result.status()),
                    progressFrom(result), result, null);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Unexpected failure while processing import resource " + resource.getId(),
                    exception);
            resource.finishProcessing(false);
            repository.save(resource);
            runRegistry.complete(runId, ImportRunStatus.FAILURE,
                    ImportRunProgress.indeterminate(0, 0, 1), null, safeMessage(exception));
        }
    }

    private static ImportRunProgress progressFrom(ImportProcessResult result) {
        long processed = result.itemsPersisted() + result.skipped();
        long total = result.filesSeen();
        return total > 0
                ? ImportRunProgress.determinate(processed, total, result.skipped(), result.processorFailures())
                : ImportRunProgress.indeterminate(processed, result.skipped(), result.processorFailures());
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
