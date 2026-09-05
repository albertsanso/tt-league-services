package org.cttelsamicsterrassa.data.load.shared.execution;

public interface ImportExecutionService {
    ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options);
}
