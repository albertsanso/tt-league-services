package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;

/**
 * How far the step a unit is running has got. {@code itemsTotal} and {@code percent} are null while the total is
 * unknown (the platform import job exposes no item progress, so FETCH_PACKAGE and IMPORT carry no counts at all);
 * {@code stage} is the ingest stage name, {@code currentItem} the last item that finished.
 */
public record UnitProgressDto(
        String step,
        String stage,
        long itemsProcessed,
        Long itemsTotal,
        Integer percent,
        String currentItem,
        Instant updatedAt) {
}
