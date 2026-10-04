package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface MatchDayJpaRepository
        extends JpaRepository<MatchDayEntity, UUID>, JpaSpecificationExecutor<MatchDayEntity> {

    List<MatchDayEntity> findBySourceAndSeason(PipelineSource source, String season);

    /** Rows of {@code [source, season]}. */
    @Query("select distinct d.source, d.season from MatchDayEntity d "
            + "where d.state <> org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState.CLOSED")
    List<Object[]> findSourceSeasonsWithUnclosedDays();
}
