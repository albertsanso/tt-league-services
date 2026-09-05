package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.Optional;

/**
 * An immutable, monotonic progress snapshot for one asynchronous import run.
 *
 * <p>{@code total} and {@code percentage} are empty whenever the current phase cannot determine a
 * reliable total ahead of time (the navigators report file/fixture counters incrementally without a
 * pre-computed total); callers must render an indeterminate state rather than fabricate a total.</p>
 */
public record ImportRunProgress(long processed, Optional<Long> total, Optional<Double> percentage,
                                long skipped, long errorCount) {

    public ImportRunProgress {
        total = total == null ? Optional.empty() : total;
        percentage = percentage == null ? Optional.empty() : percentage;
    }

    public static ImportRunProgress zero() {
        return indeterminate(0, 0, 0);
    }

    public static ImportRunProgress indeterminate(long processed, long skipped, long errorCount) {
        return new ImportRunProgress(processed, Optional.empty(), Optional.empty(), skipped, errorCount);
    }

    public static ImportRunProgress determinate(long processed, long total, long skipped, long errorCount) {
        double percentage = total <= 0 ? 0.0 : Math.min(100.0, (processed * 100.0) / total);
        return new ImportRunProgress(processed, Optional.of(total), Optional.of(percentage), skipped, errorCount);
    }
}
