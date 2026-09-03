package org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.model.ResourceJPA;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceMapperTest {

    private static final UUID ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");
    private static final Path PHYSICAL_PATH = Path.of("data", "resources", "report.html");

    @Test
    void mapsDomainResourceToJpaResource() {
        Resource resource = Resource.createExisting(
                ID, "report", "reports/report.html", PHYSICAL_PATH);

        ResourceJPA resourceJPA = new ResourceToResourceJPAMapper().apply(resource);

        assertEquals(ID, resourceJPA.getId());
        assertEquals("report", resourceJPA.getName());
        assertEquals("reports/report.html", resourceJPA.getLogicPath());
        assertEquals(PHYSICAL_PATH.toString(), resourceJPA.getPhysicalPath());
    }

    @Test
    void mapsJpaResourceToDomainResource() {
        ResourceJPA resourceJPA = new ResourceJPA(
                ID,
                "teams",
                "reports/teams.html",
                PHYSICAL_PATH.toString());

        Resource resource = new ResourceJPAToResourceMapper().apply(resourceJPA);

        assertEquals(ID, resource.getId());
        assertEquals("teams", resource.getName());
        assertEquals("reports/teams.html", resource.getLogicPath());
        assertEquals(PHYSICAL_PATH, resource.getPhysicalPath());
    }

    @Test
    void mapsNullToNull() {
        assertNull(new ResourceToResourceJPAMapper().apply(null));
        assertNull(new ResourceJPAToResourceMapper().apply(null));
    }
}
