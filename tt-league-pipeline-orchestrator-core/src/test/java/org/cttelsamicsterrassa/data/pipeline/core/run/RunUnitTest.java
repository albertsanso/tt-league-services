package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunUnitTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final Instant T3 = T0.plusSeconds(30);
    private static final Instant T4 = T0.plusSeconds(40);
    private static final RunError ERROR = new RunError("INGEST_FAILED", "boom");
    private static final ScopeFilter FILTER = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
    private static final RunScope SCOPE = new RunScope(List.of(FILTER));
    private static final String KEY = UnitKey.of(FILTER);

    private static RunUnit pending() {
        return RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 0, KEY, "SENIOR G1 · J3", SCOPE);
    }

    private static RunUnit season() {
        return RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 0, UnitKey.SEASON, "Full season",
                RunScope.fullSeason());
    }

    private static UnitProgress progress(StepKind step, long done, Long total) {
        return new UnitProgress(step, "download", done, total, "acta-1", T1);
    }

    private static RunUnit restore(UnitStatus status, Instant started, Instant finished, String ingestRunId,
            UUID importJob, RunError error, UnitProgress progress) {
        return RunUnit.restore(UUID.randomUUID(), UUID.randomUUID(), 0, KEY, "label", SCOPE, status, started,
                finished, ingestRunId, importJob, error, progress, 0);
    }

    @Test
    void plansAPendingUnitWithVersionZero() {
        RunUnit unit = pending();

        assertThat(unit.status()).isEqualTo(UnitStatus.PENDING);
        assertThat(unit.version()).isZero();
        assertThat(unit.startedAt()).isNull();
        assertThat(unit.finishedAt()).isNull();
        assertThat(unit.ingestRunId()).isNull();
        assertThat(unit.importJobId()).isNull();
        assertThat(unit.progress()).isNull();
        assertThat(unit.scope()).isEqualTo(SCOPE);
        assertThat(unit.unitKey()).isEqualTo(KEY);
    }

    @Test
    void happyPathToSucceeded() {
        UUID job = UUID.randomUUID();

        RunUnit unit = pending().startIngest("abc123", T1).packed(T2).startImport(job, T3).succeed(T4);

        assertThat(unit.status()).isEqualTo(UnitStatus.SUCCEEDED);
        assertThat(unit.startedAt()).isEqualTo(T1);
        assertThat(unit.finishedAt()).isEqualTo(T4);
        assertThat(unit.ingestRunId()).isEqualTo("abc123");
        assertThat(unit.importJobId()).isEqualTo(job);
        assertThat(unit.error()).isNull();
    }

    @Test
    void happyPathToPartialAndNoChanges() {
        assertThat(pending().startIngest("a", T1).packed(T2).startImport(UUID.randomUUID(), T3).partial(T4).status())
                .isEqualTo(UnitStatus.PARTIAL);
        RunUnit none = pending().startIngest("a", T1).noChanges(T2);
        assertThat(none.status()).isEqualTo(UnitStatus.NO_CHANGES);
        assertThat(none.finishedAt()).isEqualTo(T2);
        assertThatThrownBy(() -> none.succeed(T3)).isInstanceOf(IllegalUnitTransitionException.class);
    }

    @Test
    void replayGoesStraightFromPendingToPacked() {
        RunUnit packed = pending().startReplay(T1);

        assertThat(packed.status()).isEqualTo(UnitStatus.PACKED);
        assertThat(packed.startedAt()).isEqualTo(T1);
        assertThat(packed.ingestRunId()).isNull();
        assertThat(packed.startImport(UUID.randomUUID(), T2).status()).isEqualTo(UnitStatus.IMPORTING);
        assertThatThrownBy(() -> packed.startReplay(T2)).isInstanceOf(IllegalUnitTransitionException.class);
    }

    @Test
    void packedStillRequiresRunningIngest() {
        assertThatThrownBy(() -> pending().packed(T1)).isInstanceOf(IllegalUnitTransitionException.class);
        assertThat(pending().startIngest("a", T1).packed(T2).status()).isEqualTo(UnitStatus.PACKED);
    }

    @Test
    void aPendingUnitCanFailOrBeSkippedWithoutStarting() {
        RunUnit failed = pending().fail(ERROR, T1);
        assertThat(failed.status()).isEqualTo(UnitStatus.FAILED);
        assertThat(failed.startedAt()).isNull();
        assertThat(failed.finishedAt()).isEqualTo(T1);
        assertThat(failed.error()).isEqualTo(ERROR);

        RunUnit skipped = pending().skip(new RunError("UNIT_SKIPPED", "skipped after X"), T1);
        assertThat(skipped.status()).isEqualTo(UnitStatus.SKIPPED);
        assertThat(skipped.startedAt()).isNull();
        assertThat(skipped.error().code()).isEqualTo("UNIT_SKIPPED");
    }

    @Test
    void onlyAPendingUnitCanBeSkipped() {
        RunUnit running = pending().startIngest("a", T1);
        assertThatThrownBy(() -> running.skip(ERROR, T2)).isInstanceOf(IllegalUnitTransitionException.class);
    }

    @Test
    void importingUnitFailsKeepingItsImportJob() {
        UUID job = UUID.randomUUID();
        RunUnit unit = pending().startIngest("a", T1).packed(T2).startImport(job, T3).fail(ERROR, T4);
        assertThat(unit.importJobId()).isEqualTo(job);
        assertThat(unit.error()).isEqualTo(ERROR);
    }

    @Test
    void transitionsDoNotMutateAndKeepTheVersion() {
        RunUnit unit = pending();
        RunUnit started = unit.startIngest("a", T1);
        assertThat(unit.status()).isEqualTo(UnitStatus.PENDING);
        assertThat(started.version()).isEqualTo(unit.version());
    }

    @Test
    void illegalTransitionsThrowWithTheUnitId() {
        RunUnit unit = pending();
        assertThatThrownBy(() -> unit.startImport(UUID.randomUUID(), T1))
                .isInstanceOf(IllegalUnitTransitionException.class)
                .hasMessageContaining(unit.id().toString())
                .hasMessageContaining("PENDING")
                .hasMessageContaining("IMPORTING");
        assertThatThrownBy(() -> unit.succeed(T1)).isInstanceOf(IllegalUnitTransitionException.class);
        assertThatThrownBy(() -> unit.noChanges(T1)).isInstanceOf(IllegalUnitTransitionException.class);
        RunUnit failed = unit.fail(ERROR, T1);
        assertThatThrownBy(() -> failed.startIngest("x", T2)).isInstanceOf(IllegalUnitTransitionException.class);
        assertThatThrownBy(() -> failed.fail(ERROR, T2)).isInstanceOf(IllegalUnitTransitionException.class);
        RunUnit packed = unit.startIngest("a", T1).packed(T2);
        assertThatThrownBy(() -> packed.succeed(T3)).isInstanceOf(IllegalUnitTransitionException.class);
    }

    @Test
    void restartIngestReplacesTheIngestRunIdWithoutChangingStatusOrVersionAndClearsProgress() {
        RunUnit running = pending().startIngest("first", T1).withProgress(progress(StepKind.INGEST, 2L, 5L));

        RunUnit restarted = running.restartIngest("second", T2);

        assertThat(restarted.ingestRunId()).isEqualTo("second");
        assertThat(restarted.status()).isEqualTo(UnitStatus.RUNNING_INGEST);
        assertThat(restarted.startedAt()).isEqualTo(T1);
        assertThat(restarted.version()).isEqualTo(running.version());
        assertThat(restarted.progress()).isNull();
    }

    @Test
    void restartIngestIsOnlyAllowedWhileRunningIngestAndValidatesTheId() {
        assertThatThrownBy(() -> pending().restartIngest("x", T1)).isInstanceOf(IllegalUnitTransitionException.class);
        assertThatThrownBy(() -> pending().startIngest("a", T1).packed(T2).restartIngest("x", T3))
                .isInstanceOf(IllegalUnitTransitionException.class);
        RunUnit running = pending().startIngest("first", T1);
        assertThatThrownBy(() -> running.restartIngest(" ", T2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> running.restartIngest("x".repeat(65), T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressIsReplacedWhileRunningWithoutChangingTheVersion() {
        RunUnit running = pending().startIngest("a", T1);

        RunUnit first = running.withProgress(progress(StepKind.INGEST, 1, 10L));
        RunUnit second = first.withProgress(progress(StepKind.INGEST, 4, 10L));

        assertThat(first.progress().itemsProcessed()).isEqualTo(1);
        assertThat(second.progress().itemsProcessed()).isEqualTo(4);
        assertThat(second.version()).isEqualTo(running.version());
        assertThat(second.status()).isEqualTo(UnitStatus.RUNNING_INGEST);
        assertThat(running.progress()).isNull();
    }

    @Test
    void progressIsAllowedInEveryWorkingStatusAndRefusedElsewhere() {
        UnitProgress p = progress(StepKind.FETCH_PACKAGE, 0, null);
        RunUnit packed = pending().startIngest("a", T1).packed(T2);
        assertThat(packed.withProgress(p).progress()).isEqualTo(p);
        RunUnit importing = packed.startImport(UUID.randomUUID(), T3);
        assertThat(importing.withProgress(p).progress()).isEqualTo(p);

        assertThatThrownBy(() -> pending().withProgress(p)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> importing.succeed(T4).withProgress(p)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending().fail(ERROR, T1).withProgress(p))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyTransitionClearsTheProgress() {
        UnitProgress p = progress(StepKind.INGEST, 3, 9L);
        RunUnit running = pending().startIngest("a", T1).withProgress(p);

        assertThat(running.packed(T2).progress()).isNull();
        assertThat(running.noChanges(T2).progress()).isNull();
        assertThat(running.fail(ERROR, T2).progress()).isNull();
        RunUnit packed = running.packed(T2).withProgress(progress(StepKind.FETCH_PACKAGE, 0, null));
        assertThat(packed.startImport(UUID.randomUUID(), T3).progress()).isNull();
    }

    @Test
    void restoreRejectsBrokenInvariants() {
        UUID job = UUID.randomUUID();
        // terminal without finishedAt
        assertThatThrownBy(() -> restore(UnitStatus.NO_CHANGES, T1, null, "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // finishedAt on an active unit
        assertThatThrownBy(() -> restore(UnitStatus.RUNNING_INGEST, T1, T2, "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // FAILED without error, and SUCCEEDED with an error
        assertThatThrownBy(() -> restore(UnitStatus.FAILED, null, T1, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(UnitStatus.SUCCEEDED, T1, T2, "a", job, ERROR, null))
                .isInstanceOf(IllegalArgumentException.class);
        // SKIPPED without error or with a start
        assertThatThrownBy(() -> restore(UnitStatus.SKIPPED, null, T1, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(UnitStatus.SKIPPED, T1, T2, null, null, ERROR, null))
                .isInstanceOf(IllegalArgumentException.class);
        // IMPORTING without an import job; PACKED / PENDING with one
        assertThatThrownBy(() -> restore(UnitStatus.IMPORTING, T1, null, "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(UnitStatus.PACKED, T1, null, "a", job, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(UnitStatus.PENDING, null, null, null, job, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // started status without startedAt; pending with startedAt
        assertThatThrownBy(() -> restore(UnitStatus.RUNNING_INGEST, null, null, "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(UnitStatus.PENDING, T1, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // finishedAt before startedAt
        assertThatThrownBy(() -> restore(UnitStatus.NO_CHANGES, T2, T1, "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // progress on a terminal or pending unit
        assertThatThrownBy(() -> restore(UnitStatus.NO_CHANGES, T1, T2, "a", null, null, progress(StepKind.INGEST, 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(UnitStatus.PENDING, null, null, null, null, null, progress(StepKind.INGEST, 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreAcceptsAFailedUnitThatNeverStartedAndKeepsTheVersion() {
        RunUnit unit = RunUnit.restore(UUID.randomUUID(), UUID.randomUUID(), 2, KEY, "label", SCOPE,
                UnitStatus.FAILED, null, T1, null, null, ERROR, null, 7);

        assertThat(unit.version()).isEqualTo(7);
        assertThat(unit.ordinal()).isEqualTo(2);
    }

    @Test
    void theUnitKeyMustMatchTheScope() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> RunUnit.plan(id, id, 0, "not-the-key", "label", SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunUnit.plan(id, id, 0, UnitKey.SEASON, "label", SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunUnit.plan(id, id, 0, KEY, "label", RunScope.fullSeason()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(season().unitKey()).isEqualTo(UnitKey.SEASON);
    }

    @Test
    void onlyALegacyUnitMayHoldSeveralFilters() {
        ScopeFilter other = new ScopeFilter("JUNIOR", null, null, null, null, List.of());
        RunScope several = new RunScope(List.of(FILTER, other));
        UUID id = UUID.randomUUID();

        assertThat(RunUnit.plan(id, id, 0, UnitKey.LEGACY, "Legacy scope", several).scope()).isEqualTo(several);
        assertThatThrownBy(() -> RunUnit.plan(id, id, 0, KEY, "label", several))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesIdentityFields() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> RunUnit.plan(id, id, -1, KEY, "label", SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunUnit.plan(id, id, 0, KEY, " ", SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunUnit.plan(id, id, 0, KEY, "x".repeat(257), SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
