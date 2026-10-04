package org.cttelsamicsterrassa.data.pipeline.core.tracker.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Round progress as the platform reports it: its own today and grace period plus one entry per jornada. */
public record PlatformRoundProgress(LocalDate today, int overdueGraceDays, List<PlatformJornada> jornadas) {

    public PlatformRoundProgress {
        Objects.requireNonNull(today, "today is required");
        if (overdueGraceDays < 0) {
            throw new IllegalArgumentException("overdueGraceDays must not be negative");
        }
        Objects.requireNonNull(jornadas, "jornadas is required");
        jornadas = List.copyOf(jornadas);
    }

    /** Competition, group and phase can be null; dates are null for an undated jornada. */
    public record PlatformJornada(
            String competition,
            Integer groupNumber,
            String phase,
            int round,
            LocalDate firstDate,
            LocalDate lastDate,
            int scheduledMatches,
            int playedMatches,
            boolean open) {
    }
}
