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
    private static final Instant T4 = T0.plusSeconds(40);
    private static final RunError ERROR = new RunError("INGEST_FAILED", "boom");

    private static PipelineRun queued() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "user-1", null, T0);
    }

    private static PipelineRun restore(RunStatus status, Instant created, Instant started, Instant finished,
            UUID importJob, RunError error) {
        return PipelineRun.restore(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "u", null, status, created, started, finished, "abc", importJob, error, 0);
    }

    private static PipelineRun queue(String season, RunTrigger trigger, String by, UUID retryOf) {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, season, RunScope.fullSeason(), false,
                trigger, by, retryOf, T0);
    }

    @Test
    void forceIsKeptThroughTransitionsAndRestore() {
        PipelineRun forced = PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), true, RunTrigger.MANUAL, "user-1", null, T0);
        assertThat(forced.force()).isTrue();
        assertThat(queued().force()).isFalse();

        PipelineRun running = forced.startIngest("first", T1);
        assertThat(running.force()).isTrue();
        assertThat(running.restartIngest("second", T2).force()).isTrue();
        assertThat(running.fail(ERROR, T3).force()).isTrue();

        PipelineRun restored = PipelineRun.restore(forced.id(), forced.source(), forced.season(), forced.scope(),
                true, forced.trigger(), forced.requestedBy(), null, RunStatus.QUEUED, T0, null, null, null, null,
                null, 0);
        assertThat(restored.force()).isTrue();
    }

    @Test
    void queuesWithVersionZero() {
        PipelineRun run = queued();
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.version()).isZero();
        assertThat(run.startedAt()).isNull();
        assertThat(run.finishedAt()).isNull();
    }

    @Test
    void rejectsBadFactoryInput() {
        assertThatThrownBy(() -> queue("2025-2027", RunTrigger.MANUAL, "u", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("25-26", RunTrigger.MANUAL, "u", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, "x".repeat(129), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.RETRY, "u", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.MANUAL, "u", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryRunKeepsItsOriginalId() {
        UUID original = UUID.randomUUID();
        assertThat(queue("2025-2026", RunTrigger.RETRY, "user-1", original).retryOfRunId()).isEqualTo(original);
    }

    @Test
    void happyPathToSucceeded() {
        UUID job = UUID.randomUUID();
        PipelineRun run = queued().startIngest("abc123", T1).packed(T2).startImport(job, T3).succeed(T4);
        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.startedAt()).isEqualTo(T1);
        assertThat(run.finishedAt()).isEqualTo(T4);
        assertThat(run.ingestRunId()).isEqualTo("abc123");
        assertThat(run.importJobId()).isEqualTo(job);
        assertThat(run.error()).isNull();
    }

    @Test
    void happyPathToPartial() {
        PipelineRun run = queued().startIngest("abc", T1).packed(T2).startImport(UUID.randomUUID(), T3).partial(T4);
        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(run.finishedAt()).isEqualTo(T4);
    }

    @Test
    void noChangesIsTerminal() {
        PipelineRun run = queued().startIngest("abc", T1).noChanges(T2);
        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(run.finishedAt()).isEqualTo(T2);
        assertThatThrownBy(() -> run.succeed(T3)).isInstanceOf(IllegalRunTransitionException.class);
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
    void importingRunCanFailKeepingItsImportJob() {
        UUID job = UUID.randomUUID();
        PipelineRun run = queued().startIngest("abc", T1).packed(T2).startImport(job, T3).fail(ERROR, T4);
        assertThat(run.importJobId()).isEqualTo(job);
        assertThat(run.error()).isEqualTo(ERROR);
    }

    @Test
    void transitionsDoNotMutateAndKeepVersion() {
        PipelineRun run = queued();
        PipelineRun started = run.startIngest("abc", T1);
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(started.version()).isEqualTo(run.version());
    }

    @Test
    void illegalTransitionsThrowWithRunId() {
        PipelineRun run = queued();
        assertThatThrownBy(() -> run.packed(T1))
                .isInstanceOf(IllegalRunTransitionException.class)
                .hasMessageContaining(run.id().toString())
                .hasMessageContaining("QUEUED")
                .hasMessageContaining("PACKED");
        assertThatThrownBy(() -> run.startImport(UUID.randomUUID(), T1))
                .isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> run.succeed(T1)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> run.noChanges(T1)).isInstanceOf(IllegalRunTransitionException.class);
        PipelineRun failed = run.fail(ERROR, T1);
        assertThatThrownBy(() -> failed.startIngest("x", T2)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> failed.fail(ERROR, T2)).isInstanceOf(IllegalRunTransitionException.class);
        PipelineRun packed = run.startIngest("abc", T1).packed(T2);
        assertThatThrownBy(() -> packed.succeed(T3)).isInstanceOf(IllegalRunTransitionException.class);
    }

    @Test
    void restoreRejectsBrokenInvariants() {
        // terminal without finishedAt
        assertThatThrownBy(() -> restore(RunStatus.NO_CHANGES, T0, T1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // FAILED without error
        assertThatThrownBy(() -> restore(RunStatus.FAILED, T0, null, T1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // IMPORTING without import job
        assertThatThrownBy(() -> restore(RunStatus.IMPORTING, T0, T1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // PACKED with an import job
        assertThatThrownBy(() -> restore(RunStatus.PACKED, T0, T1, null, UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
        // started status without startedAt
        assertThatThrownBy(() -> restore(RunStatus.RUNNING_INGEST, T0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // startedAt before createdAt
        assertThatThrownBy(() -> restore(RunStatus.RUNNING_INGEST, T1, T0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // non-failed without error but with one
        assertThatThrownBy(() -> restore(RunStatus.NO_CHANGES, T0, T1, T2, null, ERROR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreAcceptsFailedWithoutStartAndKeepsVersion() {
        PipelineRun run = PipelineRun.restore(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.SCHEDULED, "system:scheduler", null, RunStatus.FAILED, T0, null,
                T1, null, null, ERROR, 3);
        assertThat(run.version()).isEqualTo(3);
    }

    @Test
    void restartIngestReplacesIngestRunIdWithoutChangingStatusOrVersion() {
        PipelineRun running = queued().startIngest("first", T1);

        PipelineRun restarted = running.restartIngest("second", T2);

        assertThat(restarted.ingestRunId()).isEqualTo("second");
        assertThat(restarted.status()).isEqualTo(RunStatus.RUNNING_INGEST);
        assertThat(restarted.startedAt()).isEqualTo(T1);
        assertThat(restarted.version()).isEqualTo(running.version());
    }

    @Test
    void restartIngestIsOnlyAllowedWhileRunningIngest() {
        assertThatThrownBy(() -> queued().restartIngest("x", T1)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> queued().startIngest("a", T1).packed(T2).restartIngest("x", T3))
                .isInstanceOf(IllegalRunTransitionException.class);
    }

    @Test
    void restartIngestValidatesTheId() {
        PipelineRun running = queued().startIngest("first", T1);
        assertThatThrownBy(() -> running.restartIngest(" ", T2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> running.restartIngest("x".repeat(65), T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PipelineRun queuedRetry() {
        return queue("2025-2026", RunTrigger.RETRY, "operator", UUID.randomUUID());
    }

    @Test
    void startReplayMovesAQueuedRetryRunStraightToPacked() {
        PipelineRun packed = queuedRetry().startReplay(T1);

        assertThat(packed.status()).isEqualTo(RunStatus.PACKED);
        assertThat(packed.startedAt()).isEqualTo(T1);
        assertThat(packed.ingestRunId()).isNull();
        assertThat(packed.startImport(UUID.randomUUID(), T2).status()).isEqualTo(RunStatus.IMPORTING);
    }

    @Test
    void startReplayIsRejectedForNonRetryRunsAndOtherStatuses() {
        assertThatThrownBy(() -> queued().startReplay(T1)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> queue("2025-2026", RunTrigger.SCHEDULED, "system", null).startReplay(T1))
                .isInstanceOf(IllegalRunTransitionException.class);
        PipelineRun packed = queuedRetry().startReplay(T1);
        assertThatThrownBy(() -> packed.startReplay(T2)).isInstanceOf(IllegalRunTransitionException.class);
    }

    @Test
    void packedStillRequiresRunningIngest() {
        assertThatThrownBy(() -> queuedRetry().packed(T1)).isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> queued().packed(T1)).isInstanceOf(IllegalRunTransitionException.class);
        assertThat(queued().startIngest("a", T1).packed(T2).status()).isEqualTo(RunStatus.PACKED);
    }
}
