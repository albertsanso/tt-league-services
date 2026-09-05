package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResourceUploadServiceTest {

    @Test
    void uploadSchedulesRepositoryLoadAfterManifestExtraction() {
        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceRepositoryLoaderService resourceRepositoryLoaderService = mock(ResourceRepositoryLoaderService.class);
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("DATA", List.of()), null);
        when(resourceZipService.extractZipAndGetManifest(new byte[]{1})).thenReturn(manifest);

        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        ResourceUploadService service = new ResourceUploadService(
                resourceRepositoryLoaderService,
                resourceZipService,
                scheduledTask::set);

        service.uploadAndTriggerAsyncLoad("resource.zip", new byte[]{1});

        verify(resourceZipService).extractZipAndGetManifest(new byte[]{1});
        verifyNoInteractions(resourceRepositoryLoaderService);

        Runnable task = scheduledTask.get();
        assertNotNull(task);
        assertSame(task, scheduledTask.get());
        task.run();

        verify(resourceRepositoryLoaderService).loadIntoRepository(manifest);
    }
}
