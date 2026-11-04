package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.util.List;

public record CleanupOutcome(List<String> purgedKeys, long purgedBytes, List<String> failedKeys) {

    public CleanupOutcome {
        purgedKeys = List.copyOf(purgedKeys);
        failedKeys = List.copyOf(failedKeys);
    }
}
