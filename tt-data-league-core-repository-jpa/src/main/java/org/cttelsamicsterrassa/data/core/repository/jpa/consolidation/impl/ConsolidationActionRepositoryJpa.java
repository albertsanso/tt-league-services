package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.repository.ConsolidationActionRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.mapper.ConsolidationActionJPAToConsolidationActionMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.mapper.ConsolidationActionToConsolidationActionJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ConsolidationActionRepositoryJpa implements ConsolidationActionRepository {
    private final ConsolidationActionRepositoryHelper consolidationActionRepositoryHelper;
    private final ConsolidationActionJPAToConsolidationActionMapper consolidationActionJPAToConsolidationActionMapper;
    private final ConsolidationActionToConsolidationActionJPAMapper consolidationActionToConsolidationActionJPAMapper;

    @Override
    public void save(ConsolidationAction action) {
        consolidationActionRepositoryHelper.save(consolidationActionToConsolidationActionJPAMapper.apply(action));
    }

    @Override
    public Optional<ConsolidationAction> findById(UUID id) {
        return consolidationActionRepositoryHelper.findById(id)
                .map(consolidationActionJPAToConsolidationActionMapper);
    }

    @Override
    public List<ConsolidationAction> findAllOrderByOccurredOnDesc() {
        return consolidationActionRepositoryHelper.findAllByOrderByOccurredOnDesc().stream()
                .map(consolidationActionJPAToConsolidationActionMapper)
                .toList();
    }

    @Override
    public List<ConsolidationAction> findAllByClubId(UUID clubId) {
        return consolidationActionRepositoryHelper.findAllByClubId(clubId).stream()
                .map(consolidationActionJPAToConsolidationActionMapper)
                .toList();
    }
}
