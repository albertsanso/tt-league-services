package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

interface PipelineRunJpaRepository extends JpaRepository<PipelineRunEntity, UUID> {

    Optional<PipelineRunEntity> findFirstBySourceAndStatusIn(PipelineSource source, Collection<RunStatus> statuses);

    List<PipelineRunEntity> findByStatusInOrderByCreatedAtAsc(Collection<RunStatus> statuses);
}
