package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportProcessResultDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartImportProcessCommandHandlerTest {
    @Test
    void processesAndPersistsAResource() {
        InMemoryImportResources repository = new InMemoryImportResources();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                ignored -> ImportProcessResult.success(List.of(), List.of(), 2, 1, 0, 0));

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        ImportProcessResultDto dto = assertInstanceOf(ImportProcessResultDto.class, response.getResponse());
        assertTrue(response.isSuccess());
        assertEquals("success", dto.status());
        assertEquals(ImportResourceStatus.PROCESSED, resource.getStatus());
        assertEquals(1, repository.saveCount);
    }

    @Test
    void retriesFinishedResourcesAndMapsEmptyResultsToError() {
        InMemoryImportResources repository = new InMemoryImportResources();
        ImportResource resource = resource(ImportResourceStatus.ERROR);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                ignored -> ImportProcessResult.empty(List.of(), List.of(), 1, 0, 0));

        handler.handle(new StartImportProcessCommand(resource.getId()));

        assertEquals(ImportResourceStatus.ERROR, resource.getStatus());
        assertEquals(1, repository.saveCount);
    }

    @Test
    void marksResourceAsErrorWhenProcessingFails() {
        InMemoryImportResources repository = new InMemoryImportResources();
        ImportResource resource = resource(ImportResourceStatus.PENDING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                ignored -> ImportProcessResult.failure(List.of(), List.of(), 1, 0, 0, 1));

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        ImportProcessResultDto dto = assertInstanceOf(ImportProcessResultDto.class, response.getResponse());
        assertTrue(response.isSuccess());
        assertEquals("failure", dto.status());
        assertEquals(ImportResourceStatus.ERROR, resource.getStatus());
        assertEquals(1, repository.saveCount);
    }

    @Test
    void rejectsAResourceAlreadyProcessing() {
        InMemoryImportResources repository = new InMemoryImportResources();
        ImportResource resource = resource(ImportResourceStatus.PROCESSING);
        repository.resources.add(resource);
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(repository,
                ignored -> { throw new AssertionError("service must not run"); });

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(resource.getId()));

        assertFalse(response.isSuccess());
        assertEquals(0, repository.saveCount);
    }

    @Test
    void returnsFailureForMissingResource() {
        UUID id = UUID.randomUUID();
        StartImportProcessCommandHandler handler = new StartImportProcessCommandHandler(
                new InMemoryImportResources(), ignored -> { throw new AssertionError("service must not run"); });

        DomainCommandResponse response = handler.handle(new StartImportProcessCommand(id));

        assertFalse(response.isSuccess());
        assertInstanceOf(ImportProcessResultDto.class, response.getResponse());
    }

    private static ImportResource resource(ImportResourceStatus status) {
        Resource source = Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/actas",
                Path.of("import", "actas"));
        return ImportResource.createExisting(UUID.randomUUID(), source, Optional.empty(), ResourceType.ACTAS,
                ZonedDateTime.now(), Optional.empty(), Season.of(2025), ImportSource.RFETM, status);
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
}
