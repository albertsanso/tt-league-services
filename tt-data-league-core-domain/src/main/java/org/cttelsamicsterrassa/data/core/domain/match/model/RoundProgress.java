package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Objects;

/**
 * The jornada progress of one competition, group and phase within a source and season (FEAT-00084).
 *
 * <p>{@code currentRound} is the highest round with at least one {@link MatchStatus#PLAYED} match and
 * is {@code null} while nothing has been played. {@code lastCompleteRound} is the highest stored round
 * with no {@link MatchStatus#SCHEDULED} match at or below it, and is {@code null} when the lowest
 * stored round is still pending. Both are derived only from stored matches: a season holds no round
 * beyond the imported snapshot, so no total number of rounds is ever inferred.</p>
 *
 * <p>The value is informational. It never decides whether a file is imported.</p>
 */
public record RoundProgress(
        ImportSource source,
        Season season,
        String competition,
        Integer groupNumber,
        String phase,
        Integer currentRound,
        Integer lastCompleteRound,
        long scheduledMatches,
        long playedMatches) {

    public RoundProgress {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        if (scheduledMatches < 0) {
            throw new IllegalArgumentException("scheduledMatches must not be negative, was " + scheduledMatches);
        }
        if (playedMatches < 0) {
            throw new IllegalArgumentException("playedMatches must not be negative, was " + playedMatches);
        }
        if (scheduledMatches + playedMatches == 0) {
            throw new IllegalArgumentException("a round progress needs at least one stored match");
        }
        if (lastCompleteRound != null && currentRound == null) {
            throw new IllegalArgumentException("lastCompleteRound requires a current round");
        }
        if (lastCompleteRound != null && currentRound != null && lastCompleteRound > currentRound) {
            throw new IllegalArgumentException(
                    "lastCompleteRound %s cannot be ahead of currentRound %s".formatted(lastCompleteRound, currentRound));
        }
    }
}
