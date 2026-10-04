package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StepTimeoutsTest {

    @Test
    void resolvesTheTimeoutOfEachKind() {
        StepTimeouts timeouts = new StepTimeouts(Duration.ofHours(3), Duration.ofMinutes(10), Duration.ofHours(4));
        assertThat(timeouts.of(StepKind.INGEST)).isEqualTo(Duration.ofHours(3));
        assertThat(timeouts.of(StepKind.FETCH_PACKAGE)).isEqualTo(Duration.ofMinutes(10));
        assertThat(timeouts.of(StepKind.IMPORT)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void rejectsNonPositiveOrMissingValues() {
        Duration one = Duration.ofSeconds(1);
        assertThatThrownBy(() -> new StepTimeouts(Duration.ZERO, one, one))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StepTimeouts(one, Duration.ofSeconds(-1), one))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StepTimeouts(one, one, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
