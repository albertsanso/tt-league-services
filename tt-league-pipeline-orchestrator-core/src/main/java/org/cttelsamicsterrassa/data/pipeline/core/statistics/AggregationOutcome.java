package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.LocalDate;
import java.util.List;

/** The days one catch-up aggregated, oldest first. */
public record AggregationOutcome(List<LocalDate> days) {

    public AggregationOutcome {
        days = List.copyOf(days);
    }

    public boolean isEmpty() {
        return days.isEmpty();
    }
}
