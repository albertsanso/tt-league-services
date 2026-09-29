package org.cttelsamicsterrassa.data.core.domain.resource.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportManifestTest {

    @Test
    void theFourArgumentConstructorDefaultsToSnapshot() {
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), Path.of("extracted"));

        assertEquals(UploadMode.SNAPSHOT, manifest.mode());
    }

    @Test
    void aNullModeIsRejected() {
        assertThrows(NullPointerException.class, () -> new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), Path.of("extracted"), null));
    }
}