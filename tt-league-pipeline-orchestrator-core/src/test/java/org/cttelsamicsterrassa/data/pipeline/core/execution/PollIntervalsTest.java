package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PollIntervalsTest {

    @Test
    void keepsPositiveValues() {
        PollIntervals polls = new PollIntervals(Duration.ofSeconds(15), Duration.ofSeconds(10));
        assertThat(polls.ingest()).isEqualTo(Duration.ofSeconds(15));
        assertThat(polls.importJob()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void rejectsNonPositiveOrMissingValues() {
        assertThatThrownBy(() -> new PollIntervals(Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PollIntervals(Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
