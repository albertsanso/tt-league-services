package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineRunTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final Instant T3 = T0.plusSeconds(30);
    private static final RunError ERROR = new RunError("INGEST_FAILED", "boom");

    private static PipelineRun queued() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "user-1", null, T0);
    }

    private static PipelineRun restore(RunStatus status, Instant created, Instant started, Instant finished,
            RunError error) {
        return PipelineRun.restore(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "u", null, null, status, created, started, finished, error, 0);
    }

    private static PipelineRun queue(String season, RunTrigger trigger, String by, UUID retryOf, UUID retryOfUnit) {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, season, RunScope.fullSeason(), false,
                trigger, by, retryOf, retryOfUnit, T0);
    }

    @Test
    void forceIsKeptThroughTransitionsAndRestore() {
        PipelineRun forced = PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), true, RunTrigger.MANUAL, "user-1", null, T0);
        assertThat(forced.force()).isTrue();
        assertThat(queued().force()).isFalse();

        PipelineRun running = forced.start(T1);
        assertThat(running.force()).isTrue();
        assertThat(running.fail(ERROR, T3).force()).isTrue();

        PipelineRun restored = PipelineRun.restore(forced.id(), forced.source(), forced.season(), forced.scope(),
                true, forced.trigger(), forced.requestedBy(), null, null, RunStatus.QUEUED, T0, null, null, null, 0);
        assertThat(restored.force()).isTrue();
    }

    @Test
    void queuesWithVersionZero() {
        PipelineRun run = queued();
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.version()).isZero();
        assertThat(run.startedAt()).isNull();
        assertThat(run.finishedAt()).isNull();
        assertThat(run.retryOfUnitId()).isNull();
    }

    @Test
    void rejectsBadFactoryInput() {
        assertThatThrownBy(() -> queue("2025-2027", RunTrigger.MANUAL, "u", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("25-26", RunTrigger.MANUAL, "u", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, "x".repeat(129), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.RETRY, "u", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, "u", UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryRunKeepsItsOriginalId() {
        UUID original = UUID.randomUUID();
        PipelineRun retry = queue("2025-2026", RunTrigger.RETRY, "user-1", original, null);
        assertThat(retry.retryOfRunId()).isEqualTo(original);
        assertThat(retry.retryOfUnitId()).isNull();
    }

    @Test
    void unitRetryRunNeedsTheOriginalRunAndUnit() {
        UUID original = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        PipelineRun retry = queue("2025-2026", RunTrigger.UNIT_RETRY, "user-1", original, unit);
        assertThat(retry.retryOfRunId()).isEqualTo(original);
        assertThat(retry.retryOfUnitId()).isEqualTo(unit);

        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.UNIT_RETRY, "u", original, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.UNIT_RETRY, "u", null, unit))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.RETRY, "u", original, unit))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, "u", null, unit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startMovesQueuedToRunning() {
        PipelineRun run = queued().start(T1);
        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.startedAt()).isEqualTo(T1);
        assertThat(run.finishedAt()).isNull();
    }

    @Test
    void finishTakesTheDerivedTerminalStatus() {
        for (RunStatus derived : new RunStatus[] {RunStatus.SUCCEEDED, RunStatus.PARTIAL, RunStatus.NO_CHANGES}) {
            PipelineRun run = queued().start(T1).finish(derived, null, T2);
            assertThat(run.status()).isEqualTo(derived);
            assertThat(run.startedAt()).isEqualTo(T1);
            assertThat(run.finishedAt()).isEqualTo(T2);
            assertThat(run.error()).isNull();
        }
        PipelineRun failed = queued().start(T1).finish(RunStatus.FAILED, ERROR, T2);
        assertThat(failed.status()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.error()).isEqualTo(ERROR);
    }

    @Test
    void finishRejectsNonTerminalStatusesAndMismatchedErrors() {
        PipelineRun running = queued().start(T1);
        assertThatThrownBy(() -> running.finish(RunStatus.RUNNING, null, T2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> running.finish(RunStatus.QUEUED, null, T2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> running.finish(RunStatus.FAILED, null, T2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> running.finish(RunStatus.SUCCEEDED, ERROR, T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aQueuedRunCannotFinishWithoutStarting() {
        assertThatThrownBy(() -> queued().finish(RunStatus.SUCCEEDED, null, T1))
                .isInstanceOf(IllegalRunTransitionException.class);
    }

    @Test
    void queuedRunCanFailBeforeStarting() {
        PipelineRun run = queued().fail(ERROR, T1);
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.startedAt()).isNull();
        assertThat(run.finishedAt()).isEqualTo(T1);
        assertThat(run.error()).isEqualTo(ERROR);
    }

    @Test
    void transitionsDoNotMutateAndKeepVersion() {
        PipelineRun run = queued();
        PipelineRun started = run.start(T1);
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(started.version()).isEqualTo(run.version());
    }

    @Test
    void terminalRunsCannotMoveAgain() {
        PipelineRun done = queued().start(T1).finish(RunStatus.SUCCEEDED, null, T2);
        assertThatThrownBy(() -> done.start(T3)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> done.fail(ERROR, T3)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> done.finish(RunStatus.PARTIAL, null, T3))
                .isInstanceOf(IllegalRunTransitionException.class);
        PipelineRun running = queued().start(T1);
        assertThatThrownBy(() -> running.start(T2))
                .isInstanceOf(IllegalRunTransitionException.class)
                .hasMessageContaining(running.id().toString())
                .hasMessageContaining("RUNNING");
    }

    @Test
    void restoreRejectsBrokenInvariants() {
        // terminal without finishedAt
        assertThatThrownBy(() -> restore(RunStatus.NO_CHANGES, T0, T1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // FAILED without error
        assertThatThrownBy(() -> restore(RunStatus.FAILED, T0, null, T1, null))
                .isInstanceOf(IllegalArgumentException.class);
        // started status without startedAt
        assertThatThrownBy(() -> restore(RunStatus.RUNNING, T0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // startedAt before createdAt
        assertThatThrownBy(() -> restore(RunStatus.RUNNING, T1, T0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // non-failed with an error
        assertThatThrownBy(() -> restore(RunStatus.NO_CHANGES, T0, T1, T2, ERROR))
                .isInstanceOf(IllegalArgumentException.class);
        // queued with a start
        assertThatThrownBy(() -> restore(RunStatus.QUEUED, T0, T1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreAcceptsFailedWithoutStartAndKeepsVersion() {
        PipelineRun run = PipelineRun.restore(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.SCHEDULED, "system:scheduler", null, null, RunStatus.FAILED,
                T0, null, T1, ERROR, 3);
        assertThat(run.version()).isEqualTo(3);
    }
}
