package org.cttelsamicsterrassa.data.core.application.match.calendar.dto;

import java.util.List;

/**
 * One competition/group/phase of the season calendar (FEAT-00092). The counts describe the whole
 * group regardless of any round-narrowing filter.
 */
public record CalendarGroupReadModel(
        Integer groupNumber,
        String phase,
        Integer currentRound,
        Integer lastCompleteRound,
        long scheduledMatches,
        long playedMatches,
        long overdueMatches,
        long postponedMatches,
        List<CalendarRoundReadModel> rounds) {
}