package org.cttelsamicsterrassa.data.core.repository.jpa.load;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ImportResourceRepositoryJpaTest {

    @Autowired
    private ImportResourceRepository importResourceRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Test
    void findsOnlyPendingImportResources() {
        ImportResource pending = importResource(ImportResourceStatus.PENDING, "pending");
        ImportResource processing = importResource(ImportResourceStatus.PROCESSING, "processing");
        ImportResource processed = importResource(ImportResourceStatus.PROCESSED, "processed");

        save(pending);
        save(processing);
        save(processed);

        assertEquals(1, importResourceRepository.findAllPendingImports().size());
        assertEquals(pending.getId(), importResourceRepository.findAllPendingImports().getFirst().getId());
        assertTrue(importResourceRepository.findAllPendingImports().stream()
                .allMatch(importResource -> importResource.getStatus() == ImportResourceStatus.PENDING));
    }

    private void save(ImportResource importResource) {
        resourceRepository.save(importResource.getResource());
        importResourceRepository.save(importResource);
    }

    private static ImportResource importResource(ImportResourceStatus status, String name) {
        Resource resource = Resource.createExisting(
                UUID.randomUUID(), name, "imports/" + name, Path.of("data", name));
        return ImportResource.createExisting(
                UUID.randomUUID(),
                resource,
                Optional.empty(),
                ResourceType.ACTAS,
                ZonedDateTime.now(),
                Optional.empty(),
                Season.of(2023),
                ImportSource.RFETM,
                status);
    }
}
