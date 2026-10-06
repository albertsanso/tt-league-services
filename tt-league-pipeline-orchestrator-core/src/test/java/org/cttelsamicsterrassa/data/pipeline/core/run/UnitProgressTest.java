package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UnitProgressTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    @Test
    void acceptsAKnownAndAnUnknownTotal() {
        UnitProgress known = new UnitProgress(StepKind.INGEST, "download", 3, 10L, "acta-3.pdf", T0);
        UnitProgress unknown = new UnitProgress(StepKind.IMPORT, null, 0, null, null, T0);

        assertThat(known.itemsTotal()).isEqualTo(10L);
        assertThat(unknown.itemsTotal()).isNull();
        assertThat(unknown.stage()).isNull();
    }

    @Test
    void rejectsNegativeAndInconsistentCounts() {
        assertThatThrownBy(() -> new UnitProgress(StepKind.INGEST, "s", -1, null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnitProgress(StepKind.INGEST, "s", 0, -1L, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnitProgress(StepKind.INGEST, "s", 5, 4L, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesTextLengthsAndRequiredFields() {
        assertThatThrownBy(() -> new UnitProgress(StepKind.INGEST, "s", 0, null, "x".repeat(257), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnitProgress(StepKind.INGEST, "s", 0, null, " ", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnitProgress(null, "s", 0, null, null, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UnitProgress(StepKind.INGEST, "s", 0, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new UnitProgress(StepKind.INGEST, "s", 0, null, "x".repeat(256), T0).currentItem()).hasSize(256);
    }

    @Test
    void sameFiguresIgnoresTheUpdateTime() {
        UnitProgress a = new UnitProgress(StepKind.INGEST, "download", 3, 10L, "a", T0);

        assertThat(a.sameFigures(new UnitProgress(StepKind.INGEST, "download", 3, 10L, "a", T0.plusSeconds(5))))
                .isTrue();
        assertThat(a.sameFigures(new UnitProgress(StepKind.INGEST, "download", 4, 10L, "a", T0))).isFalse();
        assertThat(a.sameFigures(new UnitProgress(StepKind.INGEST, "parse", 3, 10L, "a", T0))).isFalse();
        assertThat(a.sameFigures(new UnitProgress(StepKind.INGEST, "download", 3, null, "a", T0))).isFalse();
        assertThat(a.sameFigures(new UnitProgress(StepKind.INGEST, "download", 3, 10L, "b", T0))).isFalse();
        assertThat(a.sameFigures(new UnitProgress(StepKind.FETCH_PACKAGE, "download", 3, 10L, "a", T0))).isFalse();
        assertThat(a.sameFigures(null)).isFalse();
    }
}
