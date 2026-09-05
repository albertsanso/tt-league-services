package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportRunStatusDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartImportProcessCommandHandlerTest {

    private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

    @Test
    void acceptsAResourceAndCompletesTheRunAsSuccess() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> ImportProcessResult.success(List.of(), List.of(), 2, 1, 0, 0)),
                runRegistry, SAME_THREAD_EXECUTOR);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        ImportRunStatusDto accepted = assertInstanceOf(ImportRunStatusDto.class, response.getResponse());
        assertTrue(response.isSuccess());
        assertNotNull(accepted.runId());
        assertEquals(ImportResourceStatus.PROCESSED, resource.getStatus());
        assertEquals(2, repository.saveCount, "resource is saved when marked processing and again on completion");

        ImportRunSnapshot finalSnapshot = runRegistry.findByRunId(accepted.runId()).orElseThrow();
        assertEquals(ImportRunStatus.SUCCESS, finalSnapshot.status());
        assertTrue(finalSnapshot.isTerminal());
        assertTrue(runRegistry.findActiveByImportResourceId(resource.getId()).isEmpty(),
                "a terminal run is no longer active");
    }

    @Test
    void reportsProgressWhileRunningBeforeCompleting() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> {
                    listener.onProgress(ImportRunProgress.determinate(1, 2, 0, 0));
                    return ImportProcessResult.success(List.of(), List.of(), 2, 2, 0, 0);
                }), runRegistry, SAME_THREAD_EXECUTOR);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        ImportRunStatusDto accepted = (ImportRunStatusDto) response.getResponse();
        assertTrue(runRegistry.progressUpdates.stream()
                .anyMatch(progress -> progress.processed() == 1 && progress.total().orElse(-1L) == 2));
        assertEquals("success", runRegistry.findByRunId(accepted.runId()).orElseThrow().status().value());
    }

    @Test
    void retriesFinishedResourcesAndMapsEmptyResultsToTerminalErrorStatus() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.ERROR);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> ImportProcessResult.empty(List.of(), List.of(), 1, 0, 0)),
                runRegistry, SAME_THREAD_EXECUTOR);

        handler.handle(new StartImportProcessCommand(resource.getId()));

        assertEquals(ImportResourceStatus.ERROR, resource.getStatus());
        assertEquals(2, repository.saveCount);
    }

    @Test
    void marksResourceAndRunAsFailureWhenProcessingThrows() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> {
                    throw new IllegalStateException("boom");
                }), runRegistry, SAME_THREAD_EXECUTOR);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        ImportRunStatusDto accepted = (ImportRunStatusDto) response.getResponse();
        assertTrue(response.isSuccess(), "submission itself is accepted even though the run later fails");
        assertEquals(ImportResourceStatus.ERROR, resource.getStatus());
        ImportRunSnapshot finalSnapshot = runRegistry.findByRunId(accepted.runId()).orElseThrow();
        assertEquals(ImportRunStatus.FAILURE, finalSnapshot.status());
        assertEquals("boom", finalSnapshot.errorDetail().orElse(null));
    }

    @Test
    void marksResourceAndRunAsFailureWhenExecutorRejectsSubmission() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        Executor rejecting = command -> {
            throw new RejectedExecutionException("pool exhausted");
        };
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> { throw new AssertionError("service must not run"); }),
                runRegistry, rejecting);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        assertFalse(response.isSuccess());
        ImportRunStatusDto rejected = (ImportRunStatusDto) response.getResponse();
        assertEquals("failure", rejected.status());
        assertEquals(ImportResourceStatus.ERROR, resource.getStatus());
    }

    @Test
    void rejectsAResourceAlreadyProcessing() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.PROCESSING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> { throw new AssertionError("service must not run"); }),
                runRegistry, SAME_THREAD_EXECUTOR);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        assertFalse(response.isSuccess());
        assertEquals(0, repository.saveCount);
    }

    @Test
    void rejectsADuplicateSubmissionWhenARunIsAlreadyActiveForTheResource() {
        InMemoryImportResources repository = new InMemoryImportResources();
        InMemoryImportRunRegistry runRegistry = new InMemoryImportRunRegistry();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        runRegistry.registerQueued(resource.getId(), resource.getSource(), resource.getSeason().toString());
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                service((ignored, listener) -> { throw new AssertionError("service must not run"); }),
                runRegistry, SAME_THREAD_EXECUTOR);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        assertFalse(response.isSuccess());
        assertEquals(0, repository.saveCount);
    }

    @Test
    void returnsFailureForMissingResource() {
        UUID id = UUID.randomUUID();
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(
                new InMemoryImportResources(),
                service((ignored, listener) -> { throw new AssertionError("service must not run"); }),
                new InMemoryImportRunRegistry(), SAME_THREAD_EXECUTOR);

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(id));

        assertFalse(response.isSuccess());
        assertInstanceOf(ImportRunStatusDto.class, response.getResponse());
    }

    private static ImportResource resource(ImportResourceStatus status) {
        Resource source = Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/actas",
                Path.of("import", "actas"));
        return ImportResource.createExisting(UUID.randomUUID(), source, Optional.empty(), ResourceType.ACTAS,
                ZonedDateTime.now(), Optional.empty(), Season.of(2025), ImportSource.RFETM, status);
    }

    private static ImportResourceProcessService service(ProcessWithListener behaviour) {
        return new ImportResourceProcessService() {
            @Override
            public ImportProcessResult process(ImportResource importResource) {
                return process(importResource, ImportProgressListener.noop());
            }

            @Override
            public ImportProcessResult process(ImportResource importResource, ImportProgressListener listener) {
                return behaviour.process(importResource, listener);
            }
        };
    }

    @FunctionalInterface
    private interface ProcessWithListener {
        ImportProcessResult process(ImportResource resource, ImportProgressListener listener);
    }

    private static final class InMemoryImportResources implements ImportResourceRepository {
        private final List<ImportResource> resources = new ArrayList<>();
        private int saveCount;

        public Optional<ImportResource> findById(UUID id) {
            return resources.stream().filter(resource -> resource.getId().equals(id)).findFirst();
        }
        public Optional<ImportResource> findBySourceAndTypeAndSeason(String source, String type, String season) { return Optional.empty(); }
        public List<ImportResource> findAllPendingImports() { return List.of(); }
        public List<ImportResource> findBySourceAndType(String source, String type) { return List.of(); }
        public List<ImportResource> findBySource(String source) { return List.of(); }
        public List<ImportResource> findAll() { return resources; }
        public void save(ImportResource resource) { saveCount++; }
        public void deleteById(UUID id) { resources.removeIf(resource -> resource.getId().equals(id)); }
    }

    /** A minimal, non-concurrent stand-in for the registry adapter used to exercise the handler. */
    private static final class InMemoryImportRunRegistry implements ImportRunRegistry {
        private final Map<UUID, ImportRunSnapshot> runs = new LinkedHashMap<>();
        private final Map<UUID, UUID> activeByResource = new LinkedHashMap<>();
        private final List<ImportRunProgress> progressUpdates = new ArrayList<>();

        @Override
        public Optional<ImportRunSnapshot> registerQueued(UUID importResourceId, ImportSource source, String season) {
            if (activeByResource.containsKey(importResourceId)) {
                return Optional.empty();
            }
            UUID runId = UUID.randomUUID();
            ImportRunSnapshot snapshot = ImportRunSnapshot.queued(runId, importResourceId, source, season);
            runs.put(runId, snapshot);
            activeByResource.put(importResourceId, runId);
            return Optional.of(snapshot);
        }

        @Override
        public Optional<ImportRunSnapshot> markRunning(UUID runId, ImportRunProgress progress) {
            return update(runId, snapshot -> snapshot.running(progress));
        }

        @Override
        public Optional<ImportRunSnapshot> updateProgress(UUID runId, ImportRunProgress progress) {
            progressUpdates.add(progress);
            return update(runId, snapshot -> snapshot.withProgress(progress));
        }

        @Override
        public Optional<ImportRunSnapshot> complete(UUID runId, ImportRunStatus terminalStatus,
                                                    ImportRunProgress progress, ImportProcessResult result,
                                                    String errorDetail) {
            Optional<ImportRunSnapshot> updated = update(runId,
                    snapshot -> snapshot.complete(terminalStatus, progress, result, errorDetail));
            updated.ifPresent(snapshot -> activeByResource.remove(snapshot.importResourceId(), runId));
            return updated;
        }

        @Override
        public Optional<ImportRunSnapshot> findByRunId(UUID runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public Optional<ImportRunSnapshot> findActiveByImportResourceId(UUID importResourceId) {
            return Optional.ofNullable(activeByResource.get(importResourceId)).flatMap(this::findByRunId);
        }

        private Optional<ImportRunSnapshot> update(UUID runId, java.util.function.UnaryOperator<ImportRunSnapshot> mutator) {
            ImportRunSnapshot current = runs.get(runId);
            if (current == null) {
                return Optional.empty();
            }
            ImportRunSnapshot updated = mutator.apply(current);
            runs.put(runId, updated);
            return Optional.of(updated);
        }
    }
}
