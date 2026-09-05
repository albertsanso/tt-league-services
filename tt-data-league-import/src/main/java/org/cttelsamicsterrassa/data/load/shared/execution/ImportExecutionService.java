package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;

public interface ImportExecutionService {
    ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options);

    /**
     * As {@link #execute(ImportExecutionRequest, ImportExecutionOptions)}, additionally reporting
     * progress while the traversal runs. The default keeps the synchronous, progress-less behaviour
     * for existing CLI and direct callers.
     */
    default ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options,
                                          ImportProgressListener progressListener) {
        return execute(request, options);
    }
}
