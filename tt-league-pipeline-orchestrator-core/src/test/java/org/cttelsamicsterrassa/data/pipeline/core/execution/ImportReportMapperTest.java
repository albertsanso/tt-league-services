package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportCounters;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportJobState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSeasonState;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportReportMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    @Test
    void sumsCountersOverSeasonsWithAResult() {
        ImportCounters a = new ImportCounters(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        ImportCounters b = new ImportCounters(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110);
        UUID runId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ImportJobState job = new ImportJobState(jobId, "SUCCEEDED", null, List.of(
                new ImportSeasonState("2024-2025", "SUCCEEDED", null, a, List.of()),
                new ImportSeasonState("2025-2026", "SUCCEEDED", null, b, List.of())), "{\"raw\":true}");

        ImportReport report = ImportReportMapper.toReport(runId, unitId, job, NOW);

        assertThat(report.runId()).isEqualTo(runId);
        assertThat(report.unitId()).isEqualTo(unitId);
        assertThat(report.importJobId()).isEqualTo(jobId);
        assertThat(report.importStatus()).isEqualTo("SUCCEEDED");
        assertThat(report.filesSeen()).isEqualTo(11);
        assertThat(report.itemsPersisted()).isEqualTo(22);
        assertThat(report.skipped()).isEqualTo(33);
        assertThat(report.processorFailures()).isEqualTo(44);
        assertThat(report.scheduledCreated()).isEqualTo(55);
        assertThat(report.upgradedToPlayed()).isEqualTo(66);
        assertThat(report.rescheduled()).isEqualTo(77);
        assertThat(report.partialActas()).isEqualTo(88);
        assertThat(report.invalidActas()).isEqualTo(99);
        assertThat(report.unresolvedPendingFixtures()).isEqualTo(110);
        assertThat(report.amendedPlayed()).isEqualTo(121);
        assertThat(report.rawReport()).isEqualTo("{\"raw\":true}");
        assertThat(report.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void ordersIssuesJobThenSeasonErrorsThenExecutionIssues() {
        ImportJobState job = new ImportJobState(UUID.randomUUID(), "PARTIAL", "job broke", List.of(
                new ImportSeasonState("2024-2025", "FAILED", "season broke", null, List.of("late file")),
                new ImportSeasonState("2025-2026", "SUCCEEDED", null,
                        new ImportCounters(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0), List.of("odd acta"))), "{}");

        ImportReport report = ImportReportMapper.toReport(UUID.randomUUID(), UUID.randomUUID(), job, NOW);

        assertThat(report.issues()).containsExactly(
                "job broke", "2024-2025: season broke", "2024-2025: late file", "2025-2026: odd acta");
    }

    @Test
    void aSeasonWithoutAResultAddsNothingToTheCounters() {
        ImportJobState job = new ImportJobState(UUID.randomUUID(), "FAILED", null, List.of(
                new ImportSeasonState("2025-2026", "FAILED", null, null, List.of())), "{}");

        ImportReport report = ImportReportMapper.toReport(UUID.randomUUID(), UUID.randomUUID(), job, NOW);

        assertThat(report.filesSeen()).isZero();
        assertThat(report.issues()).isEmpty();
    }
}
