package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
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

    private static ImportReport report(UUID runId, UUID unitId) {
        return new ImportReport(runId, unitId, UUID.randomUUID(), "PARTIAL", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                List.of("issue one", "issue \"two\""), RAW, T0);
    }

    @Test
    void roundTripsCountersIssuesAndRawJson() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        RunUnit unit = unit(run);
        ImportReport report = report(run.id(), unit.id());
        reports.add(report);

        ImportReport loaded = reports.findByUnitId(unit.id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().ignoringFields("rawReport").isEqualTo(report);
        assertThat(loaded.runId()).isEqualTo(run.id());
        assertThat(loaded.amendedPlayed()).isEqualTo(11);
        assertThat(loaded.issues()).containsExactly("issue one", "issue \"two\"");
        assertThat(loaded.rawReport()).contains("\"jobId\"").contains("\"filesSeen\"");
    }

    @Test
    void secondReportForTheSameUnitIsRejected() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        RunUnit unit = unit(run);
        reports.add(report(run.id(), unit.id()));

        assertThatThrownBy(() -> reports.add(report(run.id(), unit.id()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyUnitOfARunHasItsOwnReport() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        List<RunUnit> units = units(run,
                new ScopeFilter("A", null, null, null, null, List.of(1)),
                new ScopeFilter("B", null, null, null, null, List.of(1)));
        ImportReport first = reports.add(report(run.id(), units.get(0).id()));
        ImportReport second = reports.add(new ImportReport(run.id(), units.get(1).id(), UUID.randomUUID(),
                "SUCCEEDED", 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), RAW, T0.plusSeconds(5)));

        assertThat(reports.findByUnitId(units.get(0).id())).get().extracting(ImportReport::importStatus)
                .isEqualTo("PARTIAL");
        assertThat(reports.findByUnitId(units.get(1).id())).get().extracting(ImportReport::importStatus)
                .isEqualTo("SUCCEEDED");
        assertThat(reports.findByRunId(run.id())).extracting(ImportReport::unitId)
                .containsExactly(first.unitId(), second.unitId());
    }

    @Test
    void missingReportIsEmpty() {
        assertThat(reports.findByUnitId(UUID.randomUUID())).isEmpty();
        assertThat(reports.findByRunId(UUID.randomUUID())).isEmpty();
    }
}
