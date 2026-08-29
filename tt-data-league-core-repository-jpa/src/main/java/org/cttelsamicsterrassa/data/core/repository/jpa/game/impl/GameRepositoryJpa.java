package org.cttelsamicsterrassa.data.core.repository.jpa.game.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameJPAToGameMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameToGameJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

@Transactional
@Component
@AllArgsConstructor
public class GameRepositoryJpa implements GameRepository {

    private final GameRepositoryHelper gameRepositoryHelper;
    private final GameJPAToGameMapper gameJPAToGameMapper;
    private final GameToGameJPAMapper gameToGameJPAMapper;

    @Override
    public List<Game> findGamesByMatchId(UUID matchId) {
        return gameRepositoryHelper.findAllByMatch_IdOrderByGameNumberAsc(matchId)
                .stream()
                .map(gameJPAToGameMapper)
                .toList();
    }

    @Override
    public List<Game> findGamesByMatchIds(Collection<UUID> matchIds) {
        return gameRepositoryHelper.findAllByMatch_IdInOrderByMatch_IdAscGameNumberAsc(matchIds)
                .stream().map(gameJPAToGameMapper).toList();
    }

    @Override
    public void saveGames(List<Game> games) {
        gameRepositoryHelper.saveAll(games.stream().map(gameToGameJPAMapper).toList());
    }
}
