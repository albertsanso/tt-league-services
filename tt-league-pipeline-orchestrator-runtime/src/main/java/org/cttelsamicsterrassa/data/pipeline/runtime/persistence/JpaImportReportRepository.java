package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaImportReportRepository implements ImportReportRepository {

    private final ImportReportJpaRepository reports;
    private final RunScopeJson json;

    JpaImportReportRepository(ImportReportJpaRepository reports, RunScopeJson json) {
        this.reports = reports;
        this.json = json;
    }

    @Override
    public ImportReport add(ImportReport report) {
        if (reports.existsById(report.runId())) {
            throw new IllegalStateException("Run " + report.runId() + " already has an import report");
        }
        ImportReportEntity entity = new ImportReportEntity(report.runId());
        entity.importJobId = report.importJobId();
        entity.importStatus = report.importStatus();
        entity.filesSeen = report.filesSeen();
        entity.itemsPersisted = report.itemsPersisted();
        entity.skipped = report.skipped();
        entity.processorFailures = report.processorFailures();
        entity.scheduledCreated = report.scheduledCreated();
        entity.upgradedToPlayed = report.upgradedToPlayed();
        entity.rescheduled = report.rescheduled();
        entity.partialActas = report.partialActas();
        entity.invalidActas = report.invalidActas();
        entity.unresolvedPendingFixtures = report.unresolvedPendingFixtures();
        entity.issues = json.writeIssues(report.issues());
        entity.rawReport = report.rawReport();
        entity.receivedAt = report.receivedAt();
        return toDomain(reports.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImportReport> findByRunId(UUID runId) {
        return reports.findById(runId).map(this::toDomain);
    }

    private ImportReport toDomain(ImportReportEntity e) {
        return new ImportReport(
                e.runId, e.importJobId, e.importStatus, e.filesSeen, e.itemsPersisted, e.skipped,
                e.processorFailures, e.scheduledCreated, e.upgradedToPlayed, e.rescheduled, e.partialActas,
                e.invalidActas, e.unresolvedPendingFixtures, json.readIssues(e.issues), e.rawReport,
                e.receivedAt);
    }
}
