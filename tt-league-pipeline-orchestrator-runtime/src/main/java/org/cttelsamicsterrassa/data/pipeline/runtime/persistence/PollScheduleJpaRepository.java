package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

interface PollScheduleJpaRepository extends JpaRepository<PollScheduleEntity, UUID> {

    List<PollScheduleEntity> findBySourceAndSeason(PipelineSource source, String season);

    List<PollScheduleEntity> findBySourceAndSeason(PipelineSource source, String season, Sort sort);

    List<PollScheduleEntity> findBySource(PipelineSource source, Sort sort);

    List<PollScheduleEntity> findBySeason(String season, Sort sort);
}
