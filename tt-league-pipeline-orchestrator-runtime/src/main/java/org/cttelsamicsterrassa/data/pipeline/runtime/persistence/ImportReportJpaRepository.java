package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportReportJpaRepository extends JpaRepository<ImportReportEntity, UUID> {
}
