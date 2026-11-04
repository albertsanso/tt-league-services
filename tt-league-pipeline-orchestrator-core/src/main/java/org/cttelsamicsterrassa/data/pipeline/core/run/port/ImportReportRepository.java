package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;

public interface ImportReportRepository {

    /** A second report for the same unit is rejected. */
    ImportReport add(ImportReport report);

    Optional<ImportReport> findByUnitId(UUID unitId);

    /** The reports of every unit of the run, in no particular order. */
    List<ImportReport> findByRunId(UUID runId);
}
