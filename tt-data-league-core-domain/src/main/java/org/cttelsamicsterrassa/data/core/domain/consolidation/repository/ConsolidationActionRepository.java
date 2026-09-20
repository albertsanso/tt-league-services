package org.cttelsamicsterrassa.data.core.domain.consolidation.repository;

import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsolidationActionRepository {

    void save(ConsolidationAction action);

    Optional<ConsolidationAction> findById(UUID id);

    List<ConsolidationAction> findAllOrderByOccurredOnDesc();

    /**
     * Finds every action in which the club appears on <em>either</em> side, so that a club id which
     * no longer resolves can still be traced.
     */
    List<ConsolidationAction> findAllByClubId(UUID clubId);
}
