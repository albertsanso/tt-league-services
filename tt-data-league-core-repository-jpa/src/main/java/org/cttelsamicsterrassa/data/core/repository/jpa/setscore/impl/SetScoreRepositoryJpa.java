package org.cttelsamicsterrassa.data.core.repository.jpa.setscore.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.mapper.SetScoreToSetScoreJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.mapper.SetScoreJPAToSetScoreMapper;

@Transactional
@Component
@AllArgsConstructor
public class SetScoreRepositoryJpa implements SetScoreRepository {

    private final SetScoreRepositoryHelper setScoreRepositoryHelper;
    private final SetScoreToSetScoreJPAMapper setScoreToSetScoreJPAMapper;
    private final SetScoreJPAToSetScoreMapper setScoreJPAToSetScoreMapper;

    @Override
    public List<SetScore> findSetScoresByGameIds(Collection<UUID> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }
        return setScoreRepositoryHelper.findAllByGameIds(gameIds).stream()
                .map(setScoreJPAToSetScoreMapper).toList();
    }

    @Override
    public void saveSetScores(List<SetScore> setScores) {
        setScoreRepositoryHelper.saveAll(setScores.stream().map(setScoreToSetScoreJPAMapper).toList());
    }
}
