package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** An inclusive range of local days, at most {@link #MAX_DAYS} long. */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 366;

    public DateRange {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("the range must not exceed " + MAX_DAYS + " days");
        }
    }

    /** Start of the first day. */
    public Instant startInstant(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    /** Start of the day after the last one (exclusive end). */
    public Instant endInstant(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
