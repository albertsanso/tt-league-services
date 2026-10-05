package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.springframework.data.jpa.repository.JpaRepository;

interface PollPolicyJpaRepository extends JpaRepository<PollPolicyEntity, PipelineSource> {

    List<PollPolicyEntity> findAllByOrderBySourceAsc();
}
