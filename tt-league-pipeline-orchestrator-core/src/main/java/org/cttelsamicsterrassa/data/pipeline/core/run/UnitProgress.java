package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.Objects;

/**
 * Finer-grained progress of the step a unit is running. {@code itemsTotal} is null while unknown; {@code stage} is the
 * ingest stage name and is null for steps that expose none.
 */
public record UnitProgress(
        StepKind step, String stage, long itemsProcessed, Long itemsTotal, String currentItem, Instant updatedAt) {

    public static final int MAX_CURRENT_ITEM = 256;

    public UnitProgress {
        Checks.required(step, "step");
        stage = Checks.optionalMax(stage, "stage", 64);
        Checks.nonNegative(itemsProcessed, "itemsProcessed");
        if (itemsTotal != null) {
            Checks.nonNegative(itemsTotal, "itemsTotal");
            if (itemsProcessed > itemsTotal) {
                throw new IllegalArgumentException("itemsProcessed must not exceed itemsTotal");
            }
        }
        currentItem = Checks.optionalMax(currentItem, "currentItem", MAX_CURRENT_ITEM);
        Checks.required(updatedAt, "updatedAt");
    }

    /** True when both carry the same figures; the update time is not compared. */
    public boolean sameFigures(UnitProgress other) {
        return other != null
                && step == other.step
                && Objects.equals(stage, other.stage)
                && itemsProcessed == other.itemsProcessed
                && Objects.equals(itemsTotal, other.itemsTotal)
                && Objects.equals(currentItem, other.currentItem);
    }
}
