package org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto;

import java.util.List;

/**
 * One competition/group/phase of the round progress (FEAT-00102). {@code currentRound} and
 * {@code lastCompleteRound} describe the whole group regardless of any filter.
 */
public record RoundProgressGroupReadModel(
        String competition,
        Integer groupNumber,
        String phase,
        Integer currentRound,
        Integer lastCompleteRound,
        List<JornadaProgressReadModel> rounds) {
}
