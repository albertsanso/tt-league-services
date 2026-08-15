package org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.MatchResult;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.model.GameJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchJPAToMatchMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonJPAToPlayerSeasonMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class GameJPAToGameMapper implements Function<GameJPA, Game> {

    MatchJPAToMatchMapper matchJPAToMatchMapper;
    PlayerSeasonJPAToPlayerSeasonMapper playerSeasonJPAToPlayerSeasonMapper;

    @Override
    public Game apply(GameJPA gameJPA) {
        if (gameJPA == null) {
            return null;
        }
        return Game.builder()
                .id(gameJPA.getId())
                .source(gameJPA.getSource() != null ? ImportSource.valueOf(gameJPA.getSource().name()) : null)
                .match(matchJPAToMatchMapper.apply(gameJPA.getMatch()))
                .gameNumber(gameJPA.getGameNumber())
                .type(gameJPA.getType().name())
                .crossover(gameJPA.getCrossover())
                .homePlayer(toPlayer(gameJPA.getHomePlayer()))
                .awayPlayer(toPlayer(gameJPA.getAwayPlayer()))
                .homeSetsWon(gameJPA.getHomeSetsWon())
                .awaySetsWon(gameJPA.getAwaySetsWon())
                .winner(toWinner(gameJPA))
                .winnerSide(gameJPA.getWinner() != null ? gameJPA.getWinner().name() : null)
                .cumulativeHomeSetsWon(gameJPA.getCumulativeHome())
                .cumulativeAwaySetsWon(gameJPA.getCumulativeAway())
                .notPlayed(gameJPA.isNotPlayed())
                .reason(gameJPA.getReason())
                .build();
    }

    private PlayerSeason toPlayer(
            PlayerSeasonJPA playerSeasonJPA) {
        return playerSeasonJPA == null ? null : playerSeasonJPAToPlayerSeasonMapper.apply(playerSeasonJPA);
    }

    private PlayerSeason toWinner(GameJPA gameJPA) {
        if (gameJPA.getWinner() == null) {
            return null;
        }
        return gameJPA.getWinner() == MatchResult.HOME
                ? toPlayer(gameJPA.getHomePlayer())
                : toPlayer(gameJPA.getAwayPlayer());
    }
}
