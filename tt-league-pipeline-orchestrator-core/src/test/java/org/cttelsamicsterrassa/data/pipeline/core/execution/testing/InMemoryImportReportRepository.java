package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryImportReportRepository implements ImportReportRepository {

    private final Map<UUID, ImportReport> reports = new LinkedHashMap<>();

    @Override
    public synchronized ImportReport add(ImportReport report) {
        if (reports.containsKey(report.unitId())) {
            throw new IllegalStateException("A report already exists for unit " + report.unitId());
        }
        reports.put(report.unitId(), report);
        return report;
    }

    @Override
    public synchronized Optional<ImportReport> findByUnitId(UUID unitId) {
        return Optional.ofNullable(reports.get(unitId));
    }

    @Override
    public synchronized List<ImportReport> findByRunId(UUID runId) {
        return reports.values().stream().filter(report -> report.runId().equals(runId)).toList();
    }
}
