package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryImportReportRepository implements ImportReportRepository {

    private final Map<UUID, ImportReport> reports = new HashMap<>();

    @Override
    public synchronized ImportReport add(ImportReport report) {
        if (reports.containsKey(report.runId())) {
            throw new IllegalStateException("A report already exists for run " + report.runId());
        }
        reports.put(report.runId(), report);
        return report;
    }

    @Override
    public synchronized Optional<ImportReport> findByRunId(UUID runId) {
        return Optional.ofNullable(reports.get(runId));
    }
}
