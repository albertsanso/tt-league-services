package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportRunStatusDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceRunService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Accepts an import start request and runs it asynchronously on {@code executor}, returning
 * immediately with a queued run snapshot. Callers poll {@link FindImportRunStatusQueryHandler} for
 * progress and the terminal outcome instead of waiting on this call.
 *
 * <p>Only one import may run at a time, system-wide: a request is rejected when the target
 * resource is already {@link ImportResourceStatus#PROCESSING}, and also when {@code runRegistry}
 * reports another resource's run is currently active (see {@link ImportRunRegistry#registerQueued}).
 * The run itself is executed by {@link ImportResourceRunService}.</p>
 */
@Named
public class StartImportProcessCommandHandler extends DomainCommandHandler<StartImportProcessCommand> {
    private final ImportResourceRepository repository;
    private final ImportResourceRunService runService;
    private final ImportRunRegistry runRegistry;
    private final Executor executor;
    private final Clock clock;

    @Inject
    public StartImportProcessCommandHandler(ImportResourceRepository repository,
                                            ImportResourceProcessService service,
                                            ImportRunRegistry runRegistry,
                                            Executor executor) {
        this(repository, service, runRegistry, executor, Clock.systemDefaultZone());
    }

    public StartImportProcessCommandHandler(ImportResourceRepository repository,
                                            ImportResourceProcessService service,
                                            ImportRunRegistry runRegistry,
                                            Executor executor,
                                            Clock clock) {
        this.repository = repository;
        this.runRegistry = runRegistry;
        this.executor = executor;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.runService = new ImportResourceRunService(repository, service, runRegistry, clock);
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
        runService.markProcessing(resource);
        try {
            executor.execute(() -> runService.run(snapshot.runId(), resource));
        } catch (RuntimeException submissionFailure) {
            ImportRunStatusDto rejected = rejectSubmission(resource, snapshot, submissionFailure);
            return DomainCommandResponse.failResponse(rejected);
        }
        return DomainCommandResponse.successResponse(ImportRunStatusDtoMapper.toDto(snapshot));
    }

    private ImportRunStatusDto rejectSubmission(ImportResource resource, ImportRunSnapshot snapshot,
                                                RuntimeException submissionFailure) {
        resource.finishProcessing(false, ZonedDateTime.now(clock));
        repository.save(resource);
        ImportRunSnapshot failed = runRegistry.complete(snapshot.runId(), ImportRunStatus.FAILURE,
                        ImportRunProgress.indeterminate(0, 0, 1), null, safeMessage(submissionFailure))
                .orElse(snapshot);
        return ImportRunStatusDtoMapper.toDto(failed);
    }

    private static String safeMessage(RuntimeException exception) {
        return ImportResourceRunService.safeMessage(exception);
    }
}
