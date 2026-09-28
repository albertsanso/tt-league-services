package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceUploadServiceTest {

    @Test
    void uploadSchedulesRepositoryLoadAfterTheShrinkCheck() {
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

        service.uploadAndTriggerAsyncLoad("resource.zip", new byte[]{1}, false);

        verify(resourceZipService).extractZipAndGetManifest(new byte[]{1});
        verify(resourceRepositoryLoaderService).verifyPublishedActasNotShrinking(manifest, false);

        Runnable task = scheduledTask.get();
        assertNotNull(task);
        assertSame(task, scheduledTask.get());
        task.run();

        verify(resourceRepositoryLoaderService).loadIntoRepository(manifest);
    }

    @Test
    void uploadRejectsAShrinkingSnapshotBeforeSchedulingTheLoad() {
        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceRepositoryLoaderService resourceRepositoryLoaderService = mock(ResourceRepositoryLoaderService.class);
        ImportManifest manifest = new ImportManifest(
                "FCTT", List.of("2026-2027"), Map.of("ACTAS", List.of()), null);
        when(resourceZipService.extractZipAndGetManifest(new byte[]{1})).thenReturn(manifest);
        doThrow(new SnapshotShrinkException(
                List.of(new SnapshotShrinkException.SeasonShrink("FCTT", "2026-2027", 3, 2))))
                .when(resourceRepositoryLoaderService).verifyPublishedActasNotShrinking(manifest, false);

        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        ResourceUploadService service = new ResourceUploadService(
                resourceRepositoryLoaderService,
                resourceZipService,
                scheduledTask::set);

        assertThrows(SnapshotShrinkException.class,
                () -> service.uploadAndTriggerAsyncLoad("resource.zip", new byte[]{1}, false));

        assertNull(scheduledTask.get());
        verify(resourceRepositoryLoaderService, never()).loadIntoRepository(manifest);
    }

    @Test
    void uploadForwardsTheOverrideFlagToTheShrinkCheck() {
        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceRepositoryLoaderService resourceRepositoryLoaderService = mock(ResourceRepositoryLoaderService.class);
        ImportManifest manifest = new ImportManifest(
                "FCTT", List.of("2026-2027"), Map.of("ACTAS", List.of()), null);
        when(resourceZipService.extractZipAndGetManifest(new byte[]{1})).thenReturn(manifest);

        ResourceUploadService service = new ResourceUploadService(
                resourceRepositoryLoaderService,
                resourceZipService,
                task -> {
                });

        service.uploadAndTriggerAsyncLoad("resource.zip", new byte[]{1}, true);

        verify(resourceRepositoryLoaderService).verifyPublishedActasNotShrinking(manifest, true);
    }

    @Test
    void shrinkCheckRunsBeforeTheScheduledLoadIsExecuted() {
        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceRepositoryLoaderService resourceRepositoryLoaderService = mock(ResourceRepositoryLoaderService.class);
        ImportManifest manifest = new ImportManifest(
                "FCTT", List.of("2026-2027"), Map.of("ACTAS", List.of()), null);
        when(resourceZipService.extractZipAndGetManifest(new byte[]{1})).thenReturn(manifest);

        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        ResourceUploadService service = new ResourceUploadService(
                resourceRepositoryLoaderService,
                resourceZipService,
                scheduledTask::set);

        service.uploadAndTriggerAsyncLoad("resource.zip", new byte[]{1}, false);

        InOrder ordered = inOrder(resourceRepositoryLoaderService);
        ordered.verify(resourceRepositoryLoaderService).verifyPublishedActasNotShrinking(manifest, false);
        scheduledTask.get().run();
        ordered.verify(resourceRepositoryLoaderService).loadIntoRepository(manifest);
    }
}
