package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportReportJpaRepository extends JpaRepository<ImportReportEntity, UUID> {

    List<ImportReportEntity> findByRunIdOrderByReceivedAtAsc(UUID runId);
}
