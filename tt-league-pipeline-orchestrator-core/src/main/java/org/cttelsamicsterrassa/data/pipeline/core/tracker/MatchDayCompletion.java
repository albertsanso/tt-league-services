package org.cttelsamicsterrassa.data.pipeline.core.tracker;

/** How far a match day is from having every result reported; computed by {@link TrackerRules#completion}. */
public enum MatchDayCompletion {
    COMPLETE,
    IN_PROGRESS,
    HAS_OVERDUE,
    FUTURE
}
