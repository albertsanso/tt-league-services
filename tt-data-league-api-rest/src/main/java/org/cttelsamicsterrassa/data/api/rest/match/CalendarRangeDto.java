package org.cttelsamicsterrassa.data.api.rest.match;

import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeFacets;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarTeamFacet;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST projection of the date-range calendar (FEAT-00093). Reuses the FEAT-00092 match row; enums
 * are strings and dates ISO values.
 */
public record CalendarRangeDto(
        String source,
        String season,
        LocalDate from,
        LocalDate to,
        LocalDate today,
        int overdueGraceDays,
        List<SeasonCalendarDto.CalendarMatchDto> matches,
        FacetsDto facets) {

    public static CalendarRangeDto from(CalendarRangeReadModel value) {
        return new CalendarRangeDto(
                value.source() == null ? null : value.source().name(),
                value.season() == null ? null : value.season().toString(),
                value.from(), value.to(), value.today(), value.overdueGraceDays(),
                value.matches().stream().map(SeasonCalendarDto.CalendarMatchDto::from).toList(),
                FacetsDto.from(value.facets()));
    }

    public record FacetsDto(List<String> competitions, List<Integer> groups, List<TeamFacetDto> teams) {
        static FacetsDto from(CalendarRangeFacets value) {
            return new FacetsDto(value.competitions(), value.groups(),
                    value.teams().stream().map(TeamFacetDto::from).toList());
        }
    }

    public record TeamFacetDto(UUID teamId, String name, String competition) {
        static TeamFacetDto from(CalendarTeamFacet value) {
            return new TeamFacetDto(value.teamId(), value.name(), value.competition());
        }
    }
}
