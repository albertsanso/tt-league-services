package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model.ConsolidationActionJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConsolidationActionRepositoryHelper extends JpaRepository<ConsolidationActionJPA, UUID> {

    List<ConsolidationActionJPA> findAllByOrderByOccurredOnDesc();

    @Query("select distinct a from ConsolidationActionJPA a join a.clubs c "
            + "where c.clubId = :clubId order by a.occurredOn desc")
    List<ConsolidationActionJPA> findAllByClubId(@Param("clubId") UUID clubId);
}
