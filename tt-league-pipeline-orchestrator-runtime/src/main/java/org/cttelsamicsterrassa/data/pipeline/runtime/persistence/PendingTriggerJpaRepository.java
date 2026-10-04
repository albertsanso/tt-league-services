package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PendingTriggerJpaRepository extends JpaRepository<PendingTriggerEntity, PipelineSource> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingTriggerEntity p where p.source = :source")
    Optional<PendingTriggerEntity> findForUpdate(@Param("source") PipelineSource source);

    List<PendingTriggerEntity> findAllByOrderByRequestedAtAsc();
}
