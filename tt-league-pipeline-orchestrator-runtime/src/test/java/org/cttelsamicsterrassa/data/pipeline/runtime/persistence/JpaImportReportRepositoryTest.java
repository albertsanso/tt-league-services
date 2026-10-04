package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaImportReportRepositoryTest extends AbstractPersistenceTest {

    private static final String RAW = "{\"jobId\":\"x\",\"seasons\":[{\"filesSeen\":3}]}";

    @Autowired
    PipelineRunRepository runs;

    @Autowired
    ImportReportRepository reports;

    private static ImportReport report(UUID runId) {
        return new ImportReport(runId, UUID.randomUUID(), "PARTIAL", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                List.of("issue one", "issue \"two\""), RAW, T0);
    }

    @Test
    void roundTripsCountersIssuesAndRawJson() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        ImportReport report = report(run.id());
        reports.add(report);

        ImportReport loaded = reports.findByRunId(run.id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().ignoringFields("rawReport").isEqualTo(report);
        assertThat(loaded.issues()).containsExactly("issue one", "issue \"two\"");
        assertThat(loaded.rawReport()).contains("\"jobId\"").contains("\"filesSeen\"");
    }

    @Test
    void secondReportForTheSameRunIsRejected() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        reports.add(report(run.id()));

        assertThatThrownBy(() -> reports.add(report(run.id()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingReportIsEmpty() {
        assertThat(reports.findByRunId(UUID.randomUUID())).isEmpty();
    }
}
