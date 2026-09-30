package org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto;

import java.util.UUID;

/** A season-specific team appearing in a calendar range. */
public record CalendarTeamFacet(UUID teamId, String name, String competition) {
}
