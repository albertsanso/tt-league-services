package org.cttelsamicsterrassa.data.core.domain.resource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadModeTest {

    @Test
    void parsesTheTwoAcceptedManifestValues() {
        assertEquals(UploadMode.SNAPSHOT, UploadMode.fromManifestValue("snapshot"));
        assertEquals(UploadMode.DELTA, UploadMode.fromManifestValue("delta"));
    }

    @Test
    void rejectsUppercaseBlankUnknownAndNullValues() {
        assertThrows(IllegalArgumentException.class, () -> UploadMode.fromManifestValue("DELTA"));
        assertThrows(IllegalArgumentException.class, () -> UploadMode.fromManifestValue("SNAPSHOT"));
        assertThrows(IllegalArgumentException.class, () -> UploadMode.fromManifestValue("merge"));
        assertThrows(IllegalArgumentException.class, () -> UploadMode.fromManifestValue(""));
        assertThrows(IllegalArgumentException.class, () -> UploadMode.fromManifestValue("  "));
        assertThrows(IllegalArgumentException.class, () -> UploadMode.fromManifestValue(null));
    }
}