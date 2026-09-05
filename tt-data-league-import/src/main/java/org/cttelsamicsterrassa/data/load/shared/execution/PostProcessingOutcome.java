package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;

import java.time.Duration;
import java.util.List;

public record PostProcessingOutcome(String phase, ConsolidationMode mode, Duration duration,
                                    long processed, List<String> warnings, List<String> errors) {
    public PostProcessingOutcome {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean successful() {
        return errors.isEmpty();
    }
}
