package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewResultDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindImportPreviewStatusQueryHandlerTest {

    @Test
    void returnsResourceScopedPreviewStatus() {
        UUID id = UUID.randomUUID();
        ImportResource importResource = ImportResource.createExisting(
                id,
                Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/RFETM/ACTAS", Path.of("imports")),
                Optional.empty(),
                ResourceType.ACTAS,
                ZonedDateTime.now(),
                Optional.empty(),
                Season.of(2025),
                ImportSource.RFETM,
                ImportResourceStatus.PENDING);
        ImportResourceRepository repository = new SingleImportResourceRepository(importResource);
        FindImportPreviewStatusQueryHandler handler = new FindImportPreviewStatusQueryHandler(
                repository,
                resource -> ImportPreviewResult.empty(List.of(), List.of(), 0, 0, 0));

        DomainQueryResponse<ImportPreviewResultDto> response = handler.handle(new FindImportPreviewStatusQuery(id));

        assertTrue(response.isSuccess());
        ImportPreviewResultDto dto = assertInstanceOf(ImportPreviewResultDto.class, response.getResponse());
        assertEquals(id, dto.importResourceId());
        assertEquals("empty-result", dto.status());
    }

    private record SingleImportResourceRepository(ImportResource importResource) implements ImportResourceRepository {
        @Override
        public Optional<ImportResource> findById(UUID id) {
            return importResource.getId().equals(id) ? Optional.of(importResource) : Optional.empty();
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
            return List.of(importResource);
        }

        @Override
        public void save(ImportResource importResource) {
            throw new AssertionError("Preview status must not save import resources");
        }

        @Override
        public void deleteById(UUID id) {
        }
    }
}
