package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportResourceRunServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T09:00:00Z"), ZoneOffset.UTC);

    private final FakeImportResourceRepository repository = new FakeImportResourceRepository();
    private final FakeImportRunRegistry registry = new FakeImportRunRegistry();

    @Test
    void markProcessingReopensAFinishedResourceAndSavesIt() {
        ImportResource resource = repository.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.ERROR));

        service(ignored -> { throw new AssertionError("must not process"); }).markProcessing(resource);

        assertEquals(ImportResourceStatus.PROCESSING, resource.getStatus());
        assertEquals(1, repository.saveCount());
    }

    @Test
    void markProcessingRejectsAResourceAlreadyProcessing() {
        ImportResource resource = repository.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.PROCESSING));

        assertThrows(IllegalStateException.class,
                () -> service(ignored -> { throw new AssertionError(); }).markProcessing(resource));
    }

    @Test
    void aSuccessfulRunEndsProcessedAndReturnsTheTerminalSnapshot() {
        ImportProcessResult result = ImportProcessResult.success(List.of(), List.of(), 3, 2, 1, 0);
        RunFixture run = runWith(ignored -> result);

        assertEquals(ImportRunStatus.SUCCESS, run.snapshot.status());
        assertEquals(Optional.of(result), run.snapshot.result());
        assertEquals(ImportResourceStatus.PROCESSED, run.resource.getStatus());
        assertEquals(Optional.of(ZonedDateTime.now(CLOCK)), run.resource.getLastProcessedDate());
        assertFalse(registry.hasActiveRun());
    }

    @Test
    void anEmptyResultEndsInErrorWithAnEmptyResultRun() {
        RunFixture run = runWith(ignored -> ImportProcessResult.empty(List.of(), List.of(), 1, 0, 0));

        assertEquals(ImportRunStatus.EMPTY_RESULT, run.snapshot.status());
        assertEquals(ImportResourceStatus.ERROR, run.resource.getStatus());
    }

    @Test
    void aFailureResultEndsInErrorWithAFailedRun() {
        RunFixture run = runWith(ignored -> ImportProcessResult.failure(List.of(), List.of(), 1, 0, 0, 1));

        assertEquals(ImportRunStatus.FAILURE, run.snapshot.status());
        assertEquals(ImportResourceStatus.ERROR, run.resource.getStatus());
        assertTrue(run.snapshot.result().isPresent());
    }

    @Test
    void aThrownExceptionFailsTheRunWithItsMessage() {
        RunFixture run = runWith(ignored -> { throw new IllegalStateException("boom"); });

        assertEquals(ImportRunStatus.FAILURE, run.snapshot.status());
        assertEquals(Optional.of("boom"), run.snapshot.errorDetail());
        assertEquals(ImportResourceStatus.ERROR, run.resource.getStatus());
        assertFalse(registry.hasActiveRun());
    }

    @Test
    void aRunUnknownToTheRegistryIsRejected() {
        ImportResource resource = repository.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.PENDING));
        ImportResourceRunService service = service(ignored -> ImportProcessResult.empty(List.of(), List.of(), 0, 0, 0));
        service.markProcessing(resource);

        assertThrows(IllegalStateException.class, () -> service.run(UUID.randomUUID(), resource));
    }

    private RunFixture runWith(Function<ImportResource, ImportProcessResult> behaviour) {
        ImportResource resource = repository.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.PENDING));
        ImportResourceRunService service = service(behaviour);
        ImportRunSnapshot queued = registry.registerQueued(resource.getId(), resource.getSource(),
                resource.getSeason().toString()).orElseThrow();
        service.markProcessing(resource);
        return new RunFixture(resource, service.run(queued.runId(), resource));
    }

    private ImportResourceRunService service(Function<ImportResource, ImportProcessResult> behaviour) {
        ImportResourceProcessService processService = new ImportResourceProcessService() {
            @Override
            public ImportProcessResult process(ImportResource importResource) {
                return behaviour.apply(importResource);
            }

            @Override
            public ImportProcessResult process(ImportResource importResource, ImportProgressListener listener) {
                listener.onProgress(ImportRunProgress.zero());
                return behaviour.apply(importResource);
            }
        };
        return new ImportResourceRunService(repository, processService, registry, CLOCK);
    }

    private record RunFixture(ImportResource resource, ImportRunSnapshot snapshot) {
    }
}
