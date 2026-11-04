package org.cttelsamicsterrassa.data.api.rest.match;

import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.JornadaProgressReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressReadModel;

import java.time.LocalDate;
import java.util.List;

/**
 * REST projection of the per-jornada round progress (FEAT-00102). Dates are ISO values. The response is
 * a contract consumed by the pipeline orchestrator: add fields only additively.
 */
public record RoundProgressDto(
        String source,
        String season,
        String competition,
        boolean onlyOpen,
        LocalDate today,
        int overdueGraceDays,
        List<RoundProgressGroupDto> groups) {

    public static RoundProgressDto from(RoundProgressReadModel value) {
        return new RoundProgressDto(
                value.source() == null ? null : value.source().name(),
                value.season() == null ? null : value.season().toString(),
                value.competition(),
                value.onlyOpen(),
                value.today(),
                value.overdueGraceDays(),
                value.groups().stream().map(RoundProgressGroupDto::from).toList());
    }

    public record RoundProgressGroupDto(
            String competition,
            Integer groupNumber,
            String phase,
            Integer currentRound,
            Integer lastCompleteRound,
            List<JornadaProgressDto> rounds) {
        static RoundProgressGroupDto from(RoundProgressGroupReadModel value) {
            return new RoundProgressGroupDto(value.competition(), value.groupNumber(), value.phase(),
                    value.currentRound(), value.lastCompleteRound(),
                    value.rounds().stream().map(JornadaProgressDto::from).toList());
        }
    }

    public record JornadaProgressDto(
            int round,
            LocalDate firstDate,
            LocalDate lastDate,
            long scheduledMatches,
            long playedMatches,
            long postponedMatches,
            long overdueMatches,
            long awaitingResultMatches,
            long undatedMatches,
            boolean complete,
            boolean current,
            boolean open) {
        static JornadaProgressDto from(JornadaProgressReadModel value) {
            return new JornadaProgressDto(value.round(), value.firstDate(), value.lastDate(),
                    value.scheduledMatches(), value.playedMatches(), value.postponedMatches(),
                    value.overdueMatches(), value.awaitingResultMatches(), value.undatedMatches(),
                    value.complete(), value.current(), value.open());
        }
    }
}
