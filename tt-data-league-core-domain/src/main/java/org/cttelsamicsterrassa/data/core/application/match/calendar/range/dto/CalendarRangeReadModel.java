package org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto;

import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.LocalDate;
import java.util.List;

/**
 * The read model of a date-range calendar (FEAT-00093): matches of every competition of a source and
 * season dated in {@code [from, to)}, plus the filter facets computed before filtering.
 */
public record CalendarRangeReadModel(
        ImportSource source,
        Season season,
        LocalDate from,
        LocalDate to,
        LocalDate today,
        int overdueGraceDays,
        List<CalendarMatchReadModel> matches,
        CalendarRangeFacets facets) {
}
