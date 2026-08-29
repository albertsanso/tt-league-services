package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.mapper.DoublesPairToDoublesPairJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.mapper.DoublesPairJPAToDoublesPairMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class DoublesPairRepositoryJpa implements DoublesPairRepository {

    private final DoublesPairRepositoryHelper doublesPairRepositoryHelper;
    private final DoublesPairToDoublesPairJPAMapper doublesPairToDoublesPairJPAMapper;
    private final DoublesPairJPAToDoublesPairMapper doublesPairJPAToDoublesPairMapper;

    @Override
    public List<DoublesPair> findDoublesPairsByGameIds(Collection<UUID> gameIds) {
        return doublesPairRepositoryHelper.findAllByGameIds(gameIds).stream()
                .map(doublesPairJPAToDoublesPairMapper).toList();
    }

    @Override
    public void saveDoublesPairs(List<DoublesPair> doublesPairs) {
        doublesPairRepositoryHelper.saveAll(
                doublesPairs.stream().map(doublesPairToDoublesPairJPAMapper).toList());
    }
}
