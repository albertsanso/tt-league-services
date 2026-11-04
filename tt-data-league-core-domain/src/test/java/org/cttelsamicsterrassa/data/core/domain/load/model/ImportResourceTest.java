package org.cttelsamicsterrassa.data.core.domain.load.model;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportResourceTest {

    @Test
    void finishProcessingSetsTheStatusAndTheLastProcessedDate() {
        ImportResource resource = resource(ImportResourceStatus.PROCESSING);
        ZonedDateTime finishedAt = ZonedDateTime.parse("2026-09-28T10:15:30+02:00[Europe/Madrid]");

        resource.finishProcessing(true, finishedAt);

        assertEquals(ImportResourceStatus.PROCESSED, resource.getStatus());
        assertEquals(Optional.of(finishedAt), resource.getLastProcessedDate());
    }

    @Test
    void finishProcessingWithAnInvalidResultStoresTheDateToo() {
        ImportResource resource = resource(ImportResourceStatus.PROCESSING);
        ZonedDateTime finishedAt = ZonedDateTime.parse("2026-09-28T10:15:30+02:00[Europe/Madrid]");

        resource.finishProcessing(false, finishedAt);

        assertEquals(ImportResourceStatus.ERROR, resource.getStatus());
        assertEquals(Optional.of(finishedAt), resource.getLastProcessedDate());
    }

    @Test
    void finishProcessingStillRejectsANonProcessingState() {
        ImportResource pending = resource(ImportResourceStatus.PENDING);
        assertThrows(IllegalStateException.class,
                () -> pending.finishProcessing(true, ZonedDateTime.now()));
        assertEquals(ImportResourceStatus.PENDING, pending.getStatus());
        assertNull(pending.getLastProcessedDate().orElse(null));
    }

    @Test
    void finishProcessingRequiresAFinishedAtTimestamp() {
        ImportResource resource = resource(ImportResourceStatus.PROCESSING);

        assertThrows(NullPointerException.class, () -> resource.finishProcessing(true, null));
        assertEquals(ImportResourceStatus.PROCESSING, resource.getStatus());
        assertTrue(resource.getLastProcessedDate().isEmpty());
    }

    private static ImportResource resource(ImportResourceStatus status) {
        Resource source = Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/actas",
                Path.of("import", "actas"));
        return ImportResource.createExisting(UUID.randomUUID(), source, Optional.empty(), ResourceType.ACTAS,
                ZonedDateTime.now(), Optional.empty(), Season.of(2025), ImportSource.RFETM, status);
    }
}
