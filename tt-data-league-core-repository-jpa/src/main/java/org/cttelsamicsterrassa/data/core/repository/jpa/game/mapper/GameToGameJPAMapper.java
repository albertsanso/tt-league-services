package org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.GameType;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.MatchResult;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.model.GameJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchToMatchJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonToPlayerSeasonJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class GameToGameJPAMapper implements Function<Game, GameJPA> {

    MatchToMatchJPAMapper matchToMatchJPAMapper;
    PlayerSeasonToPlayerSeasonJPAMapper playerSeasonToPlayerSeasonJPAMapper;

    @Override
    public GameJPA apply(Game game) {
        if (game == null) {
            return null;
        }
        GameJPA gameJPA = new GameJPA();
        gameJPA.setId(game.getId());
        gameJPA.setSource(game.getSource() != null ? Source.valueOf(game.getSource().name()) : null);
        gameJPA.setMatch(matchToMatchJPAMapper.apply(game.getMatch()));
        gameJPA.setGameNumber(game.getGameNumber());
        gameJPA.setType(GameType.valueOf(game.getType()));
        gameJPA.setCrossover(game.getCrossover());
        gameJPA.setHomePlayer(toPlayerJPA(game.getHomePlayer()));
        gameJPA.setAwayPlayer(toPlayerJPA(game.getAwayPlayer()));
        gameJPA.setHomeSetsWon(game.getHomeSetsWon());
        gameJPA.setAwaySetsWon(game.getAwaySetsWon());
        gameJPA.setWinner(toMatchResult(game));
        gameJPA.setCumulativeHome(game.getCumulativeHomeSetsWon());
        gameJPA.setCumulativeAway(game.getCumulativeAwaySetsWon());
        gameJPA.setNotPlayed(game.isNotPlayed());
        gameJPA.setReason(game.getReason());
        return gameJPA;
    }

    private PlayerSeasonJPA toPlayerJPA(
            PlayerSeason player) {
        return player == null ? null : playerSeasonToPlayerSeasonJPAMapper.apply(player);
    }

    /**
     * The stored outcome is the winning <em>side</em>. A doubles game is won by a pair and so has no
     * winning player at all, which is why {@code winnerSide} is the primary source here; the winning
     * player is only consulted as a fallback for singles.
     */
    private MatchResult toMatchResult(Game game) {
        if (game.getWinnerSide() != null) {
            return MatchResult.valueOf(game.getWinnerSide());
        }
        if (game.getWinner() == null) {
            return null;
        }
        if (game.getHomePlayer() != null && game.getWinner().getId().equals(game.getHomePlayer().getId())) {
            return MatchResult.HOME;
        }
        if (game.getAwayPlayer() != null && game.getWinner().getId().equals(game.getAwayPlayer().getId())) {
            return MatchResult.AWAY;
        }
        throw new IllegalArgumentException("Game winner must be one of the game players");
    }
}
