package org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto;

import java.util.List;

/** Filter options available in a calendar range, unaffected by the filters applied to it. */
public record CalendarRangeFacets(
        List<String> competitions,
        List<Integer> groups,
        List<CalendarTeamFacet> teams) {
}
