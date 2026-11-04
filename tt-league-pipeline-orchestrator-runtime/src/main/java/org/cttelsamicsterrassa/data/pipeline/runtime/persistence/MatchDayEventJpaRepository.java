package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MatchDayEventJpaRepository extends JpaRepository<MatchDayEventEntity, UUID> {

    List<MatchDayEventEntity> findByMatchDayIdOrderByOccurredAtAscIdAsc(UUID matchDayId);
}
