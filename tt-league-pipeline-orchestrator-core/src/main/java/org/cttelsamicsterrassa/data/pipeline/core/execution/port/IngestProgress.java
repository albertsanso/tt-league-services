package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

/**
 * What a running ingest run reports about its current stage. {@code itemsTotal} is null while the stage does not know
 * how many items it will process; {@code stage} and {@code currentItem} are null when not reported.
 */
public record IngestProgress(String stage, long itemsProcessed, Long itemsTotal, String currentItem) {

    public IngestProgress {
        if (itemsProcessed < 0) {
            throw new IllegalArgumentException("itemsProcessed must not be negative");
        }
        if (itemsTotal != null && (itemsTotal < 0 || itemsProcessed > itemsTotal)) {
            throw new IllegalArgumentException("itemsTotal must not be negative or below itemsProcessed");
        }
    }
}
