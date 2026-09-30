package org.cttelsamicsterrassa.data.api.rest.match;

import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarRoundReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.SeasonCalendarReadModel;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST projection of the season calendar (FEAT-00092). Enums are serialized as their string names
 * and dates as ISO values, following the existing match DTO conventions.
 */
public record SeasonCalendarDto(
        String source,
        String season,
        String competition,
        LocalDate today,
        int overdueGraceDays,
        List<CalendarGroupDto> groups) {

    public static SeasonCalendarDto from(SeasonCalendarReadModel value) {
        return new SeasonCalendarDto(
                name(value.source()),
                value.season() == null ? null : value.season().toString(),
                value.competition(),
                value.today(),
                value.overdueGraceDays(),
                value.groups().stream().map(CalendarGroupDto::from).toList());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public record CalendarGroupDto(
            Integer groupNumber,
            String phase,
            Integer currentRound,
            Integer lastCompleteRound,
            long scheduledMatches,
            long playedMatches,
            long overdueMatches,
            long postponedMatches,
            List<CalendarRoundDto> rounds) {
        static CalendarGroupDto from(CalendarGroupReadModel value) {
            return new CalendarGroupDto(value.groupNumber(), value.phase(), value.currentRound(),
                    value.lastCompleteRound(), value.scheduledMatches(), value.playedMatches(),
                    value.overdueMatches(), value.postponedMatches(),
                    value.rounds().stream().map(CalendarRoundDto::from).toList());
        }
    }

    public record CalendarRoundDto(
            int round,
            LocalDate firstDate,
            LocalDate lastDate,
            long scheduledMatches,
            long playedMatches,
            boolean complete,
            boolean current,
            List<CalendarMatchDto> matches) {
        static CalendarRoundDto from(CalendarRoundReadModel value) {
            return new CalendarRoundDto(value.round(), value.firstDate(), value.lastDate(),
                    value.scheduledMatches(), value.playedMatches(), value.complete(), value.current(),
                    value.matches().stream().map(CalendarMatchDto::from).toList());
        }
    }

    public record CalendarMatchDto(
            UUID id,
            ZonedDateTime dateTime,
            String city,
            String venue,
            String homeTeamName,
            String awayTeamName,
            String winnerTeamName,
            Integer homeGamesWon,
            Integer awayGamesWon,
            String status,
            String calendarState,
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
        static CalendarMatchDto from(CalendarMatchReadModel value) {
            return new CalendarMatchDto(value.id(), value.dateTime(), value.city(), value.venue(),
                    value.homeTeamName(), value.awayTeamName(), value.winnerTeamName(),
                    value.homeGamesWon(), value.awayGamesWon(), name(value.status()),
                    name(value.calendarState()), value.overdueMarked(), value.overdueMarkedAt(),
                    value.overdueMarkedBy(), value.competition(), value.groupNumber(), value.phase(),
                    value.round(), value.homeTeamId(), value.awayTeamId(), value.overdueMarkable());
        }

        private static String name(Enum<?> value) {
            return value == null ? null : value.name();
        }
    }
}