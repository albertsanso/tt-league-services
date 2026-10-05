package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JpaPipelineStepRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    PipelineRunRepository runs;

    @Autowired
    PipelineStepRepository steps;

    @Test
    void roundTripsAndUpdatesToFinished() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        PipelineStep started = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 1, T0, "abc123");
        steps.save(started);

        PipelineStep failed = started.fail(T0.plusSeconds(5), "FAILED", new RunError("E", "boom"), true);
        steps.save(failed);

        PipelineStep loaded = steps.findByRunId(run.id()).get(0);
        assertThat(loaded.status()).isEqualTo(StepStatus.FAILED);
        assertThat(loaded.retryable()).isTrue();
        assertThat(loaded.outcome()).isEqualTo("FAILED");
        assertThat(loaded.externalRef()).isEqualTo("abc123");
        assertThat(loaded.error()).isEqualTo(new RunError("E", "boom"));
        assertThat(loaded.finishedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(steps.findByRunId(run.id())).hasSize(1);
    }

    @Test
    void roundTripsTheIngestHealthAndLeavesItNullWhenUnknown() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        PipelineStep ingest = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 1, T0, "abc123");
        steps.save(ingest);
        assertThat(steps.findByRunId(run.id()).get(0).ingestHealth()).isNull();

        steps.save(ingest.succeed(T0.plusSeconds(5), "SUCCEEDED", new IngestHealth(3, 2, 1)));
        PipelineStep unknown = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 2, T0.plusSeconds(6),
                null);
        steps.save(unknown);
        steps.save(unknown.succeed(T0.plusSeconds(7), "NO_CHANGES"));

        List<PipelineStep> loaded = steps.findByRunId(run.id());
        assertThat(loaded.get(0).ingestHealth()).isEqualTo(new IngestHealth(3, 2, 1));
        assertThat(loaded.get(1).ingestHealth()).isNull();
    }

    @Test
    void duplicateRunKindAttemptIsRejected() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        steps.save(PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.IMPORT, 1, T0, null));

        assertThatThrownBy(() -> steps.save(
                PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.IMPORT, 1, T0.plusSeconds(1), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ordersByStartThenAttempt() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        PipelineStep second = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 2, T0, null);
        PipelineStep first = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 1, T0, null);
        PipelineStep later = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.FETCH_PACKAGE, 1,
                T0.plusSeconds(10), null);
        steps.save(later);
        steps.save(second);
        steps.save(first);

        assertThat(steps.findByRunId(run.id())).extracting(PipelineStep::id)
                .containsExactly(first.id(), second.id(), later.id());
    }

    @Test
    void roundTripsTheImportJobReusedFlag() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        PipelineStep importing = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.IMPORT, 1, T0, null);
        steps.save(importing);
        assertThat(steps.findByRunId(run.id()).get(0).importJobReused()).isNull();

        PipelineStep submitted = importing.withImportJob(UUID.randomUUID(), true);
        steps.save(submitted);
        steps.save(submitted.succeed(T0.plusSeconds(5), "SUCCEEDED"));

        PipelineStep loaded = steps.findByRunId(run.id()).get(0);
        assertThat(loaded.importJobReused()).isTrue();
        assertThat(loaded.externalRef()).isEqualTo(submitted.externalRef());
    }
}
