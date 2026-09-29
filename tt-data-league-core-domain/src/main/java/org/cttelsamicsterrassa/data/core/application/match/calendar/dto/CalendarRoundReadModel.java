package org.cttelsamicsterrassa.data.core.application.match.calendar.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One jornada of the season calendar (FEAT-00092). {@code firstDate}/{@code lastDate} are the
 * earliest and latest match dates in Europe/Madrid, or {@code null} when every match is undated.
 */
public record CalendarRoundReadModel(
        int round,
        LocalDate firstDate,
        LocalDate lastDate,
        long scheduledMatches,
        long playedMatches,
        boolean complete,
        boolean current,
        List<CalendarMatchReadModel> matches) {
}