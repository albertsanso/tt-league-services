package org.cttelsamicsterrassa.data.core.domain.load.job;

import java.time.Duration;
import java.util.Objects;

/**
 * How an import job waits while another import is active.
 *
 * @param busyRetryInterval how long to sleep between two checks
 * @param busyTimeout how long one wait may last before the job (or the season) fails
 */
public record ImportJobSettings(Duration busyRetryInterval, Duration busyTimeout) {

    public ImportJobSettings {
        requirePositive(busyRetryInterval, "busyRetryInterval");
        requirePositive(busyTimeout, "busyTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
