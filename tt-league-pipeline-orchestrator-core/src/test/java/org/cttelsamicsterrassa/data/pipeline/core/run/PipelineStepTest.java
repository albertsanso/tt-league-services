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
                T0.plusSeconds(1), null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.RUNNING, T0,
                T0.plusSeconds(1), null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PipelineStep.restore(id, id, StepKind.INGEST, 1, StepStatus.SUCCEEDED, T0, null,
                null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
