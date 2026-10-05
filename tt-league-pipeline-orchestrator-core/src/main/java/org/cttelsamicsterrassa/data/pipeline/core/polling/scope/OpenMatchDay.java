package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.List;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;

/** A tracker match day that is still worth polling, with the matches that are still unresolved. */
public record OpenMatchDay(MatchDayKey key, List<MatchTracking> candidates) {

    public OpenMatchDay {
        Objects.requireNonNull(key, "key is required");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates is required"));
    }
}
