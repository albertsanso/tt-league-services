package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RunArtifactTest {

    private static final String SHA = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static RunArtifact artifact(String key, String sha, long size) {
        return new RunArtifact(UUID.randomUUID(), UUID.randomUUID(), ArtifactKind.ZIP, key, sha, size, NOW);
    }

    @Test
    void acceptsRelativeKey() {
        assertThat(artifact("runs/2026/pack.zip", SHA, 0).storageKey()).isEqualTo("runs/2026/pack.zip");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/abs/pack.zip", "\\share\\pack.zip", "C:\\x\\pack.zip", "a/../b.zip", "..", "a\\..\\b"})
    void rejectsAbsoluteOrTraversingKeys(String key) {
        assertThatThrownBy(() -> artifact(key, SHA, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABCDEF", "g", "a"})
    void rejectsBadSha(String sha) {
        assertThatThrownBy(() -> artifact("k.zip", sha, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUppercaseAndWrongLengthSha() {
        assertThatThrownBy(() -> artifact("k.zip", "A".repeat(64), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> artifact("k.zip", "a".repeat(63), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> artifact("k.zip", "g".repeat(64), 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSizeAndBlankOrLongKey() {
        assertThatThrownBy(() -> artifact("k.zip", SHA, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> artifact(" ", SHA, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> artifact("k".repeat(513), SHA, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
