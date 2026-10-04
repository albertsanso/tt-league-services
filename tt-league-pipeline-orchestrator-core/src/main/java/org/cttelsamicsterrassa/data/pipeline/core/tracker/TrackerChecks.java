package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.Objects;

/** Argument validation shared by the tracker values. */
final class TrackerChecks {

    private TrackerChecks() {
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
}
