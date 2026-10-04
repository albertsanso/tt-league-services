package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PipelineRunJpaRepository extends JpaRepository<PipelineRunEntity, UUID>, JpaSpecificationExecutor<PipelineRunEntity> {

    Optional<PipelineRunEntity> findFirstBySourceAndStatusIn(PipelineSource source, Collection<RunStatus> statuses);

    List<PipelineRunEntity> findByStatusInOrderByCreatedAtAsc(Collection<RunStatus> statuses);
}
