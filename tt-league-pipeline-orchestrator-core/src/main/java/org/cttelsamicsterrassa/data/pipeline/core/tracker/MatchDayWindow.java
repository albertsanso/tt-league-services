package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.time.LocalDate;

/**
 * Dates of a match day as the platform reports them. The window runs from the first match date to the last one plus
 * the grace days (the last day a result can still be awaited). An undated match day has no window.
 */
public record MatchDayWindow(LocalDate firstDate, LocalDate lastDate, int graceDays) {

    public MatchDayWindow {
        if ((firstDate == null) != (lastDate == null)) {
            throw new IllegalArgumentException("firstDate and lastDate must both be set or both be null");
        }
        if (firstDate != null && firstDate.isAfter(lastDate)) {
            throw new IllegalArgumentException("firstDate must not be after lastDate");
        }
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must not be negative");
        }
    }

    public static MatchDayWindow undated(int graceDays) {
        return new MatchDayWindow(null, null, graceDays);
    }

    public boolean isDated() {
        return firstDate != null;
    }

    /** First day of the window, or null when undated. */
    public LocalDate start() {
        return firstDate;
    }

    /** Last day of the window ({@code lastDate + graceDays}), or null when undated. */
    public LocalDate end() {
        return isDated() ? lastDate.plusDays(graceDays) : null;
    }

    /** False for an undated window. */
    public boolean hasStarted(LocalDate today) {
        return isDated() && !today.isBefore(firstDate);
    }

    /** False for an undated window. */
    public boolean contains(LocalDate today) {
        return hasStarted(today) && !today.isAfter(end());
    }
}
