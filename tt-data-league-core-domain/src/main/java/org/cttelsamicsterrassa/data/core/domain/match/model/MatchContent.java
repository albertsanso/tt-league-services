package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The full playable content of one match (FEAT-00080): the {@code PLAYED} header together with the
 * lineups, games, set scores and doubles pairs that belong to it, validated as one unit before any
 * repository applies it. A replacement keeps the match id and its natural key; only the results and
 * children change.
 *
 * <p>Every child must reference this match (or one of its games), so a half-consistent payload is
 * rejected here rather than partially persisted. Domain-level validation only: the transaction and
 * the delete-then-insert write are owned by the repository implementations.</p>
 */
public record MatchContent(
        Match match,
        List<Lineup> lineups,
        List<Game> games,
        List<SetScore> setScores,
        List<DoublesPair> doublesPairs) {

    public MatchContent {
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(match.getId(), "match id is required");
        if (match.getStatus() != MatchStatus.PLAYED) {
            throw new IllegalArgumentException(
                    "MatchContent requires a PLAYED match but was " + match.getStatus());
        }
        lineups = List.copyOf(Objects.requireNonNull(lineups, "lineups is required"));
        games = List.copyOf(Objects.requireNonNull(games, "games is required"));
        setScores = List.copyOf(Objects.requireNonNull(setScores, "setScores is required"));
        doublesPairs = List.copyOf(Objects.requireNonNull(doublesPairs, "doublesPairs is required"));

        UUID matchId = match.getId();
        for (Lineup lineup : lineups) {
            requireSameMatch(lineup.getMatch() == null ? null : lineup.getMatch().getId(), matchId,
                    "lineup", lineup.getId());
        }
        for (Game game : games) {
            requireSameMatch(game.getMatch() == null ? null : game.getMatch().getId(), matchId,
                    "game", game.getId());
        }
        Set<UUID> gameIds = games.stream().map(Game::getId).collect(Collectors.toSet());
        for (SetScore setScore : setScores) {
            requireKnownGame(setScore.getGame() == null ? null : setScore.getGame().getId(), gameIds,
                    "set score", setScore.getId());
        }
        for (DoublesPair pair : doublesPairs) {
            requireKnownGame(pair.getGame() == null ? null : pair.getGame().getId(), gameIds,
                    "doubles pair", pair.getId());
        }
    }

    private static void requireSameMatch(UUID childMatchId, UUID matchId, String kind, UUID childId) {
        if (!matchId.equals(childMatchId)) {
            throw new IllegalArgumentException(
                    kind + " " + childId + " belongs to match " + childMatchId + ", not " + matchId);
        }
    }

    private static void requireKnownGame(UUID gameId, Set<UUID> gameIds, String kind, UUID childId) {
        if (gameId == null || !gameIds.contains(gameId)) {
            throw new IllegalArgumentException(
                    kind + " " + childId + " references game " + gameId + " which is not part of the content");
        }
    }
}
