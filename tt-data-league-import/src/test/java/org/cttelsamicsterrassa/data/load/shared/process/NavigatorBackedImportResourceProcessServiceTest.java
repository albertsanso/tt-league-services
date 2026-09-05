package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionMetrics;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionRequest;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionResult;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorBackedImportResourceProcessServiceTest {

    @Test
    void forwardsTheProgressListenerToTheExecutionService() {
        AtomicReference<Object> receivedListener = new AtomicReference<>();
        ImportExecutionService executionService = new ImportExecutionService() {
            @Override
            public ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options) {
                throw new AssertionError("the listener overload must be used");
            }

            @Override
            public ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options,
                                                 org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener listener) {
                receivedListener.set(listener);
                listener.onProgress(ImportRunProgress.determinate(1, 2, 0, 0));
                return new ImportExecutionResult(request.source(), request.season().map(Object::toString),
                        ImportProcessStatus.SUCCESS, new ImportExecutionMetrics(2, 1, 0, 0, 0, 5), List.of(), List.of());
            }
        };
        NavigatorBackedImportResourceProcessService service =
                new NavigatorBackedImportResourceProcessService(executionService);
        java.util.List<ImportRunProgress> observed = new java.util.ArrayList<>();

        ImportProcessResult result = service.process(resource(), observed::add);

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertEquals(1, observed.size());
        assertEquals(1, observed.get(0).processed());
        assertTrue(receivedListener.get() != null);
    }

    @Test
    void theListenerLessOverloadStillWorks() {
        ImportExecutionService executionService = new ImportExecutionService() {
            @Override
            public ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options) {
                return new ImportExecutionResult(request.source(), request.season().map(Object::toString),
                        ImportProcessStatus.EMPTY_RESULT, new ImportExecutionMetrics(0, 0, 0, 0, 0, 1), List.of(), List.of());
            }
        };
        NavigatorBackedImportResourceProcessService service =
                new NavigatorBackedImportResourceProcessService(executionService);

        ImportProcessResult result = service.process(resource());

        assertEquals(ImportProcessStatus.EMPTY_RESULT, result.status());
    }

    private static ImportResource resource() {
        Resource source = Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/actas",
                Path.of("import", "actas"));
        return ImportResource.createExisting(UUID.randomUUID(), source, Optional.empty(), ResourceType.ACTAS,
                ZonedDateTime.now(), Optional.empty(), Season.of(2025),
                ImportSource.RFETM, org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus.PENDING);
    }
}
