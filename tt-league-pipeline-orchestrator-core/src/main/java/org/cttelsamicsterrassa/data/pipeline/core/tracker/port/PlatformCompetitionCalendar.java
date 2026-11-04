package org.cttelsamicsterrassa.data.pipeline.core.tracker.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Every match of one competition, flattened; {@code calendarState} is the platform's derived state. */
public record PlatformCompetitionCalendar(LocalDate today, List<PlatformCalendarMatch> matches) {

    public PlatformCompetitionCalendar {
        Objects.requireNonNull(today, "today is required");
        Objects.requireNonNull(matches, "matches is required");
        matches = List.copyOf(matches);
    }

    /** Group, phase, date and the result fields (games won per side, winner) can be null. */
    public record PlatformCalendarMatch(
            UUID id,
            String competition,
            Integer groupNumber,
            String phase,
            int round,
            Instant dateTime,
            String homeTeamName,
            String awayTeamName,
            String status,
            String calendarState,
            Integer homeGamesWon,
            Integer awayGamesWon,
            String winnerTeamName) {
    }
}
