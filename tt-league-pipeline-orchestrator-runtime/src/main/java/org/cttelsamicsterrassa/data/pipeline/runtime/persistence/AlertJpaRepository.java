package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {

    List<AlertEntity> findByClearedAtIsNullOrderByRaisedAtAscIdAsc();
}
