package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewResultDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewFinding;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
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

class StartImportPreviewCommandHandlerTest {

    @Test
    void previewsAnExistingImportResourceWithoutSavingIt() {
        InMemoryImportResources repository = new InMemoryImportResources();
        ImportResource importResource = importResource(UUID.randomUUID());
        repository.resources.add(importResource);
        StartImportPreviewCommandHandler handler = new StartImportPreviewCommandHandler(
                repository,
                resource -> ImportPreviewResult.success(
                        List.of(new ImportPreviewFinding("info", "validated", null)),
                        List.of(),
                        1,
                        1,
                        0,
                        0));

        DomainCommandResponse response = handler.handle(new StartImportPreviewCommand(importResource.getId()));

        assertTrue(response.isSuccess());
        ImportPreviewResultDto dto = assertInstanceOf(ImportPreviewResultDto.class, response.getResponse());
        assertEquals(importResource.getId(), dto.importResourceId());
        assertEquals("success", dto.status());
        assertEquals(1, dto.validationFindings().size());
        assertEquals(0, repository.saveCount);
    }

    @Test
    void returnsFailurePayloadWhenTheResourceDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        StartImportPreviewCommandHandler handler = new StartImportPreviewCommandHandler(
                new InMemoryImportResources(),
                resource -> {
                    throw new AssertionError("Preview service must not run for a missing resource");
                });

        DomainCommandResponse response = handler.handle(new StartImportPreviewCommand(missingId));

        assertFalse(response.isSuccess());
        ImportPreviewResultDto dto = assertInstanceOf(ImportPreviewResultDto.class, response.getResponse());
        assertEquals("failure", dto.status());
        assertEquals(missingId, dto.importResourceId());
    }

    private static ImportResource importResource(UUID id) {
        Resource resource = Resource.createExisting(
                UUID.randomUUID(),
                "ACTAS",
                "import/RFETM/ACTAS",
                Path.of("import-rfetm", "actas"));
        return ImportResource.createExisting(
                id,
                resource,
                Optional.empty(),
                ResourceType.ACTAS,
                ZonedDateTime.now(),
                Optional.empty(),
                Season.of(2025),
                ImportSource.RFETM,
                org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus.PENDING);
    }

    private static final class InMemoryImportResources implements ImportResourceRepository {
        private final List<ImportResource> resources = new ArrayList<>();
        private int saveCount;

        @Override
        public Optional<ImportResource> findById(UUID id) {
            return resources.stream().filter(resource -> resource.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<ImportResource> findBySourceAndTypeAndSeason(String source, String type, String season) {
            return Optional.empty();
        }

        @Override
        public List<ImportResource> findAllPendingImports() {
            return List.of();
        }

        @Override
        public List<ImportResource> findBySourceAndType(String source, String type) {
            return List.of();
        }

        @Override
        public List<ImportResource> findBySource(String source) {
            return List.of();
        }

        @Override
        public List<ImportResource> findAll() {
            return resources;
        }

        @Override
        public void save(ImportResource importResource) {
            saveCount++;
            resources.add(importResource);
        }

        @Override
        public void deleteById(UUID id) {
            resources.removeIf(resource -> resource.getId().equals(id));
        }
    }
}
