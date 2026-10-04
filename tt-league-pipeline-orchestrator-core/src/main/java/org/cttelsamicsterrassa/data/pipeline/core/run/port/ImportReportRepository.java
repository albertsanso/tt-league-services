package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;

public interface ImportReportRepository {

    /** A second report for the same run is rejected. */
    ImportReport add(ImportReport report);

    Optional<ImportReport> findByRunId(UUID runId);
}
