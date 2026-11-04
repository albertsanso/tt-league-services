package org.cttelsamicsterrassa.data.core.application.importresource.shared.dto;

/**
 * One jornada-progress row of an import resource (FEAT-00084): the current round, the last complete
 * round and the scheduled/played match counts of one competition, group and phase.
 *
 * <p>{@code source} and {@code season} are not repeated per row because the enclosing DTO already
 * carries them. {@code currentRound} and {@code lastCompleteRound} are {@code null} while nothing has
 * been played and while the lowest stored round is still pending, respectively. The row is derived
 * live from stored matches and is informational only.</p>
 */
public record RoundProgressDto(
        String competition,
        Integer groupNumber,
        String phase,
        Integer currentRound,
        Integer lastCompleteRound,
        long scheduledMatches,
        long playedMatches) {
}
