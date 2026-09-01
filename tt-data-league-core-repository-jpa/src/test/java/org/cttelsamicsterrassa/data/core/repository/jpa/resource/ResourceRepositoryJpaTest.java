package org.cttelsamicsterrassa.data.core.repository.jpa.resource;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ResourceRepositoryJpaTest {

    private static final UUID ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");

    @Autowired
    private ResourceRepository resourceRepository;

    @Test
    void persistsAndFindsResources() {
        Resource resource = resource("report", ResourceType.SEASON_REPORTS);

        resourceRepository.save(resource);

        assertEquals(ID, resourceRepository.findById(ID).orElseThrow().getId());
        assertEquals(ID, resourceRepository.findByTypeAndName(
                ResourceType.SEASON_REPORTS, "report").orElseThrow().getId());
        assertEquals(1, resourceRepository.findAllByType(ResourceType.SEASON_REPORTS).size());
        assertEquals(1, resourceRepository.findAll().size());
    }

    @Test
    void deletesResourcesById() {
        resourceRepository.save(resource("report", ResourceType.SEASON_REPORTS));

        resourceRepository.deleteById(ID);

        assertTrue(resourceRepository.findById(ID).isEmpty());
    }

    private static Resource resource(String name, ResourceType type) {
        return Resource.createExisting(
                ID, type, name, "reports/" + name + ".html", Path.of("data", name + ".html"));
    }
}
