package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineStepTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final RunError ERROR = new RunError("E", "failed");

    private static PipelineStep started() {
        return PipelineStep.start(UUID.randomUUID(), UUID.randomUUID(), StepKind.INGEST, 1, T0, "abc");
    }

    @Test
    void startsRunning() {
        PipelineStep step = started();
        assertThat(step.status()).isEqualTo(StepStatus.RUNNING);
        assertThat(step.finishedAt()).isNull();
    }

    @Test
    void succeeds() {
        PipelineStep step = started().succeed(T0.plusSeconds(5), "NO_CHANGES");
        assertThat(step.status()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(step.outcome()).isEqualTo("NO_CHANGES");
        assertThat(step.finishedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void failsWithErrorAndRetryable() {
        PipelineStep step = started().fail(T0.plusSeconds(5), "FAILED", ERROR, true);
        assertThat(step.status()).isEqualTo(StepStatus.FAILED);
        assertThat(step.retryable()).isTrue();
        assertThat(step.error()).isEqualTo(ERROR);
    }

    @Test
    void onlyRunningStepsCanFinish() {
        PipelineStep done = started().succeed(T0.plusSeconds(1), "OK");
        assertThatThrownBy(() -> done.succeed(T0.plusSeconds(2), "OK")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> done.fail(T0.plusSeconds(2), "X", ERROR, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesFields() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> PipelineStep.start(id, id, StepKind.IMPORT, 0, T0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PipelineStep.start(id, id, StepKind.IMPORT, 1, T0, "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> started().succeed(T0.plusSeconds(1), "x".repeat(33)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> started().succeed(T0.minusSeconds(1), "OK"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreRejectsInconsistentState() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.FAILED, T0,
                T0.plusSeconds(1), null, null, null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.RUNNING, T0,
                T0.plusSeconds(1), null, null, null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.SUCCEEDED, T0, null,
                null, null, null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ingestStepKeepsTheHealthItFinishedWith() {
        IngestHealth health = new IngestHealth(2, 1, 3);
        PipelineStep succeeded = started().succeed(T0.plusSeconds(5), "SUCCEEDED", health);
        PipelineStep failed = started().fail(T0.plusSeconds(5), "FAILED", ERROR, false, health);

        assertThat(succeeded.ingestHealth()).isEqualTo(health);
        assertThat(failed.ingestHealth()).isEqualTo(health);
        assertThat(started().ingestHealth()).isNull();
        assertThat(started().succeed(T0.plusSeconds(5), "SUCCEEDED").ingestHealth()).isNull();
    }

    @Test
    void healthIsRejectedOnNonIngestStepsAndWhileRunning() {
        IngestHealth health = new IngestHealth(0, 0, 0);
        PipelineStep importing =
                PipelineStep.start(UUID.randomUUID(), UUID.randomUUID(), StepKind.IMPORT, 1, T0, "abc");
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> importing.succeed(T0.plusSeconds(1), "SUCCEEDED", health))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.RUNNING, T0, null,
                null, null, null, null, null, health, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void healthRejectsNegativeValues() {
        assertThatThrownBy(() -> new IngestHealth(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IngestHealth(0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IngestHealth(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attachesExternalRefOnceWhileRunning() {
        PipelineStep step = PipelineStep.start(UUID.randomUUID(), UUID.randomUUID(), StepKind.INGEST, 1, T0, null)
                .withExternalRef("ingest-1");

        assertThat(step.externalRef()).isEqualTo("ingest-1");
        assertThat(step.status()).isEqualTo(StepStatus.RUNNING);
        assertThatThrownBy(() -> step.withExternalRef("again")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void externalRefIsRejectedWhenBlankTooLongOrStepFinished() {
        PipelineStep fresh = PipelineStep.start(UUID.randomUUID(), UUID.randomUUID(), StepKind.INGEST, 1, T0, null);
        assertThatThrownBy(() -> fresh.withExternalRef(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fresh.withExternalRef("x".repeat(65))).isInstanceOf(IllegalStateException.class);
        PipelineStep finished = fresh.succeed(T0.plusSeconds(1), "OK");
        assertThatThrownBy(() -> finished.withExternalRef("late")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void importJobCarriesTheReusedFlagOnImportStepsOnly() {
        UUID job = UUID.randomUUID();
        PipelineStep step = PipelineStep.start(UUID.randomUUID(), UUID.randomUUID(), StepKind.IMPORT, 1, T0, null);
        assertThat(step.importJobReused()).isNull();

        PipelineStep reused = step.withImportJob(job, true);
        assertThat(reused.externalRef()).isEqualTo(job.toString());
        assertThat(reused.importJobReused()).isTrue();
        assertThat(reused.succeed(T0.plusSeconds(1), "SUCCEEDED").importJobReused()).isTrue();
        assertThatThrownBy(() -> reused.withImportJob(job, true)).isInstanceOf(IllegalStateException.class);

        PipelineStep ingest = started();
        assertThatThrownBy(() -> ingest.withImportJob(job, false)).isInstanceOf(IllegalStateException.class);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.RUNNING, T0, null,
                null, null, null, null, null, null, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
