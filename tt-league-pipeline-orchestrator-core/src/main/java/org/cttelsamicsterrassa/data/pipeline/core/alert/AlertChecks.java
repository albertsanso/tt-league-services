package org.cttelsamicsterrassa.data.pipeline.core.alert;

import java.time.Duration;
import java.util.Objects;

/** Argument validation shared by the alert values. */
final class AlertChecks {

    private AlertChecks() {
    }

    static <T> T required(T value, String name) {
        return Objects.requireNonNull(value, name + " is required");
    }

    static String nonBlankMax(String value, String name, int max) {
        required(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return max(value, name, max);
    }

    static String optionalMax(String value, String name, int max) {
        if (value == null) {
            return null;
        }
        return nonBlankMax(value, name, max);
    }

    static String max(String value, String name, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException(name + " must be at most " + max + " characters");
        }
        return value;
    }

    static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    static Duration positive(Duration value, String name) {
        required(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
