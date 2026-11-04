package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MatchTrackingJpaRepository extends JpaRepository<MatchTrackingEntity, UUID> {

    List<MatchTrackingEntity> findByMatchDayIdIn(Collection<UUID> matchDayIds);

    /** Rows of {@code [matchDayId, status, matches, ignoredMatches]}. */
    @Query("select m.matchDayId, m.status, count(m), sum(case when m.ignoredAt is not null then 1 else 0 end) "
            + "from MatchTrackingEntity m where m.matchDayId in :ids group by m.matchDayId, m.status")
    List<Object[]> countByDayAndStatus(@Param("ids") Collection<UUID> matchDayIds);
}
