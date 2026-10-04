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
        assertEquals(ManifestProvenance.EMPTY, manifest.provenance());
    }

    @Test
    void theFiveArgumentConstructorHasNoProvenance() {
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), Path.of("extracted"), UploadMode.DELTA);

        assertEquals(UploadMode.DELTA, manifest.mode());
        assertEquals(ManifestProvenance.EMPTY, manifest.provenance());
    }

    @Test
    void aNullProvenanceIsRejected() {
        assertThrows(NullPointerException.class, () -> new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), Path.of("extracted"),
                UploadMode.SNAPSHOT, null));
    }

    @Test
    void aNullModeIsRejected() {
        assertThrows(NullPointerException.class, () -> new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), Path.of("extracted"), null));
    }
}