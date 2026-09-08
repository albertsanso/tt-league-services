package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionRequest;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionResult;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class NavigatorBackedImportResourceProcessService
        implements org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService {
    private final ImportExecutionService executionService;
    private final ImportExecutionOptions executionOptions;
    private final Supplier<Path> rfetmTeamsFolderResolver;

    /**
     * @param rfetmTeamsFolderResolver when non-null, re-resolved on every call and used to override
     *                                 {@code executionOptions.rfetmTeamsFolder()} so administrator changes to
     *                                 the persisted RFETM teams folder setting take effect without a restart.
     */
    public NavigatorBackedImportResourceProcessService(ImportExecutionService executionService,
                                                       ImportExecutionOptions executionOptions,
                                                       Supplier<Path> rfetmTeamsFolderResolver) {
        this.executionService = executionService;
        this.executionOptions = executionOptions;
        this.rfetmTeamsFolderResolver = rfetmTeamsFolderResolver;
    }

    public NavigatorBackedImportResourceProcessService(ImportExecutionService executionService,
                                                       ImportExecutionOptions executionOptions) {
        this(executionService, executionOptions, null);
    }

    public NavigatorBackedImportResourceProcessService(ImportExecutionService executionService) {
        this(executionService, ImportExecutionOptions.defaults(), null);
    }

    @Override
    public ImportProcessResult process(ImportResource resource) {
        return process(resource, ImportProgressListener.noop());
    }

    @Override
    public ImportProcessResult process(ImportResource resource, ImportProgressListener listener) {
        if (resource.getType() != ResourceType.ACTAS) {
            return ImportProcessResult.failure(List.of(), List.of(new ImportPreviewProcessingError(
                    "%s import supports ACTAS resources only; %s resources have no match-report navigator."
                            .formatted(resource.getSource(), resource.getType()),
                    resource.getResource().getPhysicalPath().toString())), 0, 0, 0, 0);
        }
        ImportExecutionRequest request = new ImportExecutionRequest(resource.getSource(),
                resource.getResource().getPhysicalPath(), java.util.Optional.ofNullable(resource.getSeason()));
        ImportExecutionResult result = executionService.execute(request, effectiveOptions(), listener);
        List<ImportPreviewProcessingError> errors = result.issues().stream()
                .map(issue -> new ImportPreviewProcessingError(issue.message(), issue.location()))
                .toList();
        long files = result.metrics().filesSeen();
        long dispatched = result.metrics().itemsDispatched();
        long skipped = result.metrics().skipped();
        long failures = result.metrics().processorFailures();
        return new ImportProcessResult(result.status(), List.of(), errors, files, dispatched, skipped, failures,
                result.metrics().elapsedMillis(), result.metrics().persistenceWrites(),
                result.issues().stream().map(issue -> issue.processor() + ": " + issue.message()).toList(),
                result.postProcessing().stream().map(Object::toString).toList());
    }

    private ImportExecutionOptions effectiveOptions() {
        if (rfetmTeamsFolderResolver == null) {
            return executionOptions;
        }
        return new ImportExecutionOptions(executionOptions.clubConsolidationMode(),
                executionOptions.playerConsolidationMode(), rfetmTeamsFolderResolver.get(),
                executionOptions.batchSize());
    }
}
