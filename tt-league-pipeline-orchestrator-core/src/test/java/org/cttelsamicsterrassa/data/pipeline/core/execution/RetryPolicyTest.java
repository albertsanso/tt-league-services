package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final RetryPolicy DEFAULTS =
            new RetryPolicy(3, Duration.ofSeconds(30), 2, Duration.ofMinutes(5));

    @Test
    void delaysDoubleUntilRetriesAreUsedUp() {
        assertThat(DEFAULTS.delayBeforeRetry(0)).contains(Duration.ofSeconds(30));
        assertThat(DEFAULTS.delayBeforeRetry(1)).contains(Duration.ofSeconds(60));
        assertThat(DEFAULTS.delayBeforeRetry(2)).contains(Duration.ofSeconds(120));
        assertThat(DEFAULTS.delayBeforeRetry(3)).isEmpty();
        assertThat(DEFAULTS.delayBeforeRetry(4)).isEmpty();
    }

    @Test
    void delayIsCappedAtMaxBackoff() {
        RetryPolicy capped = new RetryPolicy(5, Duration.ofSeconds(30), 4, Duration.ofSeconds(100));
        assertThat(capped.delayBeforeRetry(0)).contains(Duration.ofSeconds(30));
        assertThat(capped.delayBeforeRetry(1)).contains(Duration.ofSeconds(100));
        assertThat(capped.delayBeforeRetry(4)).contains(Duration.ofSeconds(100));
    }

    @Test
    void zeroRetriesNeverRetries() {
        assertThat(new RetryPolicy(0, Duration.ofSeconds(1), 1, Duration.ofSeconds(1)).delayBeforeRetry(0)).isEmpty();
    }

    @Test
    void validatesItsArguments() {
        assertThatThrownBy(() -> new RetryPolicy(-1, Duration.ofSeconds(1), 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(11, Duration.ofSeconds(1), 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofSeconds(1), 0.5, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofSeconds(2), 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DEFAULTS.delayBeforeRetry(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
