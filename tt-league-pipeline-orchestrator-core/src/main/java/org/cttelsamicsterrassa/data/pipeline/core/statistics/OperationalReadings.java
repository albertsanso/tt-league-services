package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** One reading of the operational gauges: pending matches and open match days, for every {@link PipelineSource}. */
public record OperationalReadings(
        Instant asOf,
        Map<PipelineSource, PendingByAge.SourcePending> pending,
        Map<PipelineSource, Integer> openMatchDays) {

    public OperationalReadings {
        Objects.requireNonNull(asOf, "asOf is required");
        pending = complete(pending, "pending");
        openMatchDays = complete(openMatchDays, "openMatchDays");
    }

    private static <T> Map<PipelineSource, T> complete(Map<PipelineSource, T> values, String name) {
        Objects.requireNonNull(values, name + " is required");
        for (PipelineSource source : PipelineSource.values()) {
            if (values.get(source) == null) {
                throw new IllegalArgumentException(name + " has no entry for " + source);
            }
        }
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }
}
