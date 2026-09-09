package org.cttelsamicsterrassa.data.api.runtime.importrun;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryImportRunRegistryTest {

    @Test
    void queuedRunsToRunningToTerminalTransitionsArePersisted() {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        UUID resourceId = UUID.randomUUID();

        ImportRunSnapshot queued = registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026").orElseThrow();
        assertEquals(ImportRunStatus.QUEUED, queued.status());

        ImportRunSnapshot running = registry.markRunning(queued.runId(), ImportRunProgress.zero()).orElseThrow();
        assertEquals(ImportRunStatus.RUNNING, running.status());

        ImportRunSnapshot progressed = registry.updateProgress(queued.runId(),
                ImportRunProgress.determinate(1, 2, 0, 0)).orElseThrow();
        assertEquals(1, progressed.progress().processed());

        ImportProcessResult result = ImportProcessResult.success(List.of(), List.of(), 2, 2, 0, 0);
        ImportRunSnapshot completed = registry.complete(queued.runId(), ImportRunStatus.SUCCESS,
                ImportRunProgress.determinate(2, 2, 0, 0), result, null).orElseThrow();
        assertEquals(ImportRunStatus.SUCCESS, completed.status());
        assertTrue(completed.result().isPresent());
        assertTrue(registry.findActiveByImportResourceId(resourceId).isEmpty(),
                "a terminal run must no longer be reported as active");
    }

    @Test
    void rejectsASecondQueuedRunWhileOneIsAlreadyActiveForTheSameResource() {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        UUID resourceId = UUID.randomUUID();
        registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026").orElseThrow();

        Optional<ImportRunSnapshot> second = registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026");

        assertTrue(second.isEmpty());
    }

    @Test
    void rejectsAQueuedRunForAnotherResourceWhileOneIsAlreadyActive() {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.registerQueued(first, ImportSource.RFETM, "2025-2026").orElseThrow();

        Optional<ImportRunSnapshot> rejected = registry.registerQueued(second, ImportSource.BCNESA, "2025-2026");

        assertTrue(rejected.isEmpty(), "only one import may run at a time, regardless of resource or source");
    }

    @Test
    void allowsARunForAnotherResourceOnceThePreviousOneReachedATerminalStatus() {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ImportRunSnapshot firstRun = registry.registerQueued(first, ImportSource.RFETM, "2025-2026").orElseThrow();
        registry.complete(firstRun.runId(), ImportRunStatus.SUCCESS, ImportRunProgress.zero(),
                ImportProcessResult.success(List.of(), List.of(), 0, 0, 0, 0), null);

        Optional<ImportRunSnapshot> secondRun = registry.registerQueued(second, ImportSource.BCNESA, "2025-2026");

        assertTrue(secondRun.isPresent());
    }

    @Test
    void onlyOneConcurrentSubmissionWinsAcrossDifferentResources() throws InterruptedException {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        int attempts = 20;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            for (int i = 0; i < attempts; i++) {
                UUID resourceId = UUID.randomUUID();
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026").isPresent()) {
                        accepted.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, accepted.get(), "exactly one concurrent submission may be accepted system-wide");
    }

    @Test
    void allowsANewRunAfterThePreviousOneReachedATerminalStatus() {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        UUID resourceId = UUID.randomUUID();
        ImportRunSnapshot first = registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026").orElseThrow();
        registry.complete(first.runId(), ImportRunStatus.FAILURE, ImportRunProgress.zero(), null, "boom");

        Optional<ImportRunSnapshot> retry = registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026");

        assertTrue(retry.isPresent());
        assertFalse(retry.get().runId().equals(first.runId()));
    }

    @Test
    void onlyOneConcurrentSubmissionWinsForTheSameResource() throws InterruptedException {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();
        UUID resourceId = UUID.randomUUID();
        int attempts = 20;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            for (int i = 0; i < attempts; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (registry.registerQueued(resourceId, ImportSource.RFETM, "2025-2026").isPresent()) {
                        accepted.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, accepted.get(), "exactly one concurrent submission may be accepted for the same resource");
    }

    @Test
    void findByRunIdReturnsEmptyForAnUnknownRun() {
        InMemoryImportRunRegistry registry = new InMemoryImportRunRegistry();

        assertTrue(registry.findByRunId(UUID.randomUUID()).isEmpty());
    }
}
