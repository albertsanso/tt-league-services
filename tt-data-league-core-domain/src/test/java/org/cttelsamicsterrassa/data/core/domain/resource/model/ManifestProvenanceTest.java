package org.cttelsamicsterrassa.data.core.domain.resource.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestProvenanceTest {

    @Test
    void emptyHasNoValues() {
        assertTrue(ManifestProvenance.EMPTY.runId().isEmpty());
        assertTrue(ManifestProvenance.EMPTY.generator().isEmpty());
        assertTrue(ManifestProvenance.EMPTY.generatorVersion().isEmpty());
        assertTrue(ManifestProvenance.EMPTY.contentSha256().isEmpty());
        assertTrue(ManifestProvenance.EMPTY.matchCounts().isEmpty());
    }

    @Test
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new ManifestProvenance(
                null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new ManifestProvenance(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), null));
    }
}
