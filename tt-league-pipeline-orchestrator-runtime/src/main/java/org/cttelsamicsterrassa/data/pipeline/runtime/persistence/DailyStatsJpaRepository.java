package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DailyStatsJpaRepository extends JpaRepository<DailyStatsEntity, DailyStatsEntity.Key> {

    List<DailyStatsEntity> findByStatDateBetween(LocalDate from, LocalDate to);

    List<DailyStatsEntity> findByStatDateBetweenAndSourceIn(
            LocalDate from, LocalDate to, Collection<PipelineSource> sources);

    @Query("select max(d.statDate) from DailyStatsEntity d")
    Optional<LocalDate> findLatestDate();
}
