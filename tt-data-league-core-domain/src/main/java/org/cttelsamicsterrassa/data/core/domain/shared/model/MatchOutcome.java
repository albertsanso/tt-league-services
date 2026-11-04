package org.cttelsamicsterrassa.data.core.domain.shared.model;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * A match's outcome from one team's perspective (FEAT-00066). Imports can store unplayed fixtures
 * as {@code SCHEDULED} (FEAT-00077); both outcome methods return {@link Optional#empty()} for a
 * SCHEDULED match before any other rule runs, so a fixture is never reported as played. A
 * winner-less {@code PLAYED} match is a genuine tie: a {@code null} {@link Match#getWinnerTeam()}
 * on a played match never means "not yet played".
 *
 * <p>Draws are shown only at team level, and only for {@link TieEligibleCompetitions}; everywhere
 * else - and always at player level - a tied match is excluded entirely from stats and match
 * records rather than shown with a different label, so both outcome methods return
 * {@link Optional#empty()} for a match that must not be displayed at all.</p>
 */
public enum MatchOutcome {
    WIN, LOSS, DRAW;

    /**
     * The outcome for team-level stats and match-record displays. Empty when the match has no
     * winner and its competition is not in {@link TieEligibleCompetitions} - callers must treat
     * that as "this match does not exist" for counts and lists.
     */
    public static Optional<MatchOutcome> teamOutcome(Match match, UUID perspectiveTeamId) {
        if (perspectiveTeamId == null || match.getStatus() == MatchStatus.SCHEDULED) {
            return Optional.empty();
        }
        if (match.getWinnerTeam() == null) {
            return TieEligibleCompetitions.isTieEligible(match.getCompetition())
                    ? Optional.of(DRAW) : Optional.empty();
        }
        return Optional.of(perspectiveTeamId.equals(match.getWinnerTeam().getId()) ? WIN : LOSS);
    }

    /**
     * The outcome for player-level stats and match-record displays. Draws never apply at player
     * level, in any competition, so a tied match always returns empty here.
     */
    public static Optional<MatchOutcome> playerOutcome(Match match, UUID perspectiveTeamId) {
        if (perspectiveTeamId == null || match.getStatus() == MatchStatus.SCHEDULED
                || match.getWinnerTeam() == null) {
            return Optional.empty();
        }
        return Optional.of(perspectiveTeamId.equals(match.getWinnerTeam().getId()) ? WIN : LOSS);
    }
}
