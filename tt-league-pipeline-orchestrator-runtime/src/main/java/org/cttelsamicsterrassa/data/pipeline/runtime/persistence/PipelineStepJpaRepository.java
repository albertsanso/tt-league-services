package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PipelineStepJpaRepository extends JpaRepository<PipelineStepEntity, UUID> {

    List<PipelineStepEntity> findByRunIdOrderByStartedAtAscAttemptAsc(UUID runId);
}
