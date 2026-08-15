package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.mapper.DoublesPairToDoublesPairJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Transactional
@Component
@AllArgsConstructor
public class DoublesPairRepositoryJpa implements DoublesPairRepository {

    private final DoublesPairRepositoryHelper doublesPairRepositoryHelper;
    private final DoublesPairToDoublesPairJPAMapper doublesPairToDoublesPairJPAMapper;

    @Override
    public void saveDoublesPairs(List<DoublesPair> doublesPairs) {
        doublesPairRepositoryHelper.saveAll(
                doublesPairs.stream().map(doublesPairToDoublesPairJPAMapper).toList());
    }
}
