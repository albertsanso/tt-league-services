package org.cttelsamicsterrassa.data.core.application.match.calendar.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.LocalDate;
import java.util.List;

/**
 * The read model of one competition's season calendar (FEAT-00092).
 */
public record SeasonCalendarReadModel(
        ImportSource source,
        Season season,
        String competition,
        LocalDate today,
        int overdueGraceDays,
        List<CalendarGroupReadModel> groups) {
}