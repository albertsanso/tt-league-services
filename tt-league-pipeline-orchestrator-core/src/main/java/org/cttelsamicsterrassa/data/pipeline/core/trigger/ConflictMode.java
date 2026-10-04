package org.cttelsamicsterrassa.data.pipeline.core.trigger;

/** What to do with a trigger for a source that already has an active run. */
public enum ConflictMode {
    REJECT,
    /** Store it as the source's single pending trigger and launch it when the active run ends. */
    QUEUE
}
