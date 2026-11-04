package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.Objects;

/** Platform identity of a group as the tracker knows it: competition, group number and (BCNESA only) phase. */
public record PlatformGroupKey(String competition, Integer groupNumber, String phase) {

    public PlatformGroupKey {
        Objects.requireNonNull(competition, "competition is required");
    }
}
