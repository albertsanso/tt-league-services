package org.cttelsamicsterrassa.data.core.application.match.calendar.dto;

import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * One match row of the season calendar (FEAT-00092; competition, group, phase, round and team ids
 * added by FEAT-00093). The three manual-mark fields are filled only
 * for a SCHEDULED match ({@code false}/{@code null} for a PLAYED match). {@code overdueMarkable} tells
 * whether the match may be manually marked overdue today (a dated SCHEDULED match, from the day after
 * its date).
 */
public record CalendarMatchReadModel(
        UUID id,
        ZonedDateTime dateTime,
        String city,
        String venue,
        String homeTeamName,
        String awayTeamName,
        String winnerTeamName,
        Integer homeGamesWon,
        Integer awayGamesWon,
        MatchStatus status,
        CalendarMatchState calendarState,
        boolean overdueMarked,
        ZonedDateTime overdueMarkedAt,
        String overdueMarkedBy,
        String competition,
        Integer groupNumber,
        String phase,
        int round,
        UUID homeTeamId,
        UUID awayTeamId,
        boolean overdueMarkable) {
}