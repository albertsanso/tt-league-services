package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RunQueryTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    private static RunQuery query(Instant from, Instant to, int page, int size) {
        return new RunQuery(Set.of(), Set.of(), from, to, page, size);
    }

    @Test
    void acceptsTheBoundaries() {
        assertThat(query(null, null, 0, 1).size()).isEqualTo(1);
        assertThat(query(T0, T0, 0, 100).size()).isEqualTo(100);
    }

    @Test
    void rejectsInvalidPagingAndRange() {
        assertThatThrownBy(() -> query(null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query(null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query(T0.plusSeconds(1), T0, 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageComputesTotalPages() {
        assertThat(new RunPage(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(new RunPage(List.of(), 0, 20, 20).totalPages()).isEqualTo(1);
        assertThat(new RunPage(List.of(), 0, 20, 21).totalPages()).isEqualTo(2);
    }
}
