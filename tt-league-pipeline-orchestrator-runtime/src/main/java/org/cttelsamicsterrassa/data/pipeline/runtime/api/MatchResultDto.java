package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.UUID;

/** The platform's view of one tracked match; the result fields are null until the match is played. */
public record MatchResultDto(
        UUID matchId, String platformStatus, Integer homeGamesWon, Integer awayGamesWon, String winnerTeamName) {
}
