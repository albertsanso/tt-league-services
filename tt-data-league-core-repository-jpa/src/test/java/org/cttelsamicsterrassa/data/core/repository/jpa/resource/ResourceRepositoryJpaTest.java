package org.cttelsamicsterrassa.data.core.repository.jpa.resource;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
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
        Resource resource = resource("report");

        resourceRepository.save(resource);

        assertEquals(ID, resourceRepository.findById(ID).orElseThrow().getId());
        assertEquals(ID, resourceRepository.findByLogicPathAndName(
                "reports/report.html", "report").orElseThrow().getId());
        assertEquals(1, resourceRepository.findAllByLogicPath("reports/report.html").size());
        assertEquals(1, resourceRepository.findAll().size());
    }

    @Test
    void deletesResourcesById() {
        resourceRepository.save(resource("report"));

        resourceRepository.deleteById(ID);

        assertTrue(resourceRepository.findById(ID).isEmpty());
    }

    private static Resource resource(String name) {
        return Resource.createExisting(
                ID, name, "reports/" + name + ".html", Path.of("data", name + ".html"));
    }
}
