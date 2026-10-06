package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.junit.jupiter.api.Test;

class UnitRetryRulesTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final RunError ERROR = new RunError("INGEST_FAILED", "boom");
    private static final ScopeFilter FILTER = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));

    private static PipelineRun run() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026", new RunScope(List.of(FILTER)),
                false, RunTrigger.MANUAL, "user", null, T0);
    }

    private static PipelineRun finished(PipelineRun run, RunStatus status) {
        return run.start(T1).finish(status, status == RunStatus.FAILED ? ERROR : null, T2);
    }

    private static RunUnit pending(PipelineRun run) {
        return RunUnit.plan(UUID.randomUUID(), run.id(), 0, UnitKey.of(FILTER), "SENIOR G1 · J3",
                new RunScope(List.of(FILTER)));
    }

    private static RunUnit failed(PipelineRun run) {
        return pending(run).startIngest("i", T1).fail(ERROR, T2);
    }

    @Test
    void aFailedUnitOfAFinishedRunIsRetryable() {
        PipelineRun run = run();

        UnitRetryEligibility eligibility =
                UnitRetryRules.check(finished(run, RunStatus.PARTIAL), failed(run), Optional.empty());

        assertThat(eligibility.allowed()).isTrue();
        assertThat(eligibility.code()).isNull();
    }

    @Test
    void aSkippedUnitIsRetryable() {
        PipelineRun run = run();
        RunUnit skipped = pending(run).skip(new RunError("UNIT_SKIPPED", "skipped after X on unit Y"), T2);

        assertThat(UnitRetryRules.check(finished(run, RunStatus.FAILED), skipped, Optional.empty()).allowed())
                .isTrue();
    }

    @Test
    void aTimedOutUnitIsFailedSoItIsRetryable() {
        PipelineRun run = run();
        RunUnit timedOut = pending(run).startIngest("i", T1).fail(new RunError("STEP_TIMEOUT", "too slow"), T2);

        assertThat(UnitRetryRules.check(finished(run, RunStatus.FAILED), timedOut, Optional.empty()).allowed())
                .isTrue();
    }

    @Test
    void anActiveRunBlocksTheRetry() {
        PipelineRun run = run();

        UnitRetryEligibility queued = UnitRetryRules.check(run, failed(run), Optional.empty());
        UnitRetryEligibility running = UnitRetryRules.check(run.start(T1), failed(run), Optional.empty());

        assertThat(queued).isEqualTo(UnitRetryEligibility.no(UnitRetryEligibility.RUN_ACTIVE));
        assertThat(running).isEqualTo(UnitRetryEligibility.no(UnitRetryEligibility.RUN_ACTIVE));
    }

    @Test
    void anActiveRunOfTheSourceBlocksTheRetry() {
        PipelineRun run = run();
        PipelineRun other = run();

        UnitRetryEligibility eligibility =
                UnitRetryRules.check(finished(run, RunStatus.PARTIAL), failed(run), Optional.of(other));

        assertThat(eligibility).isEqualTo(UnitRetryEligibility.no(UnitRetryEligibility.RUN_ACTIVE));
    }

    @Test
    void aUnitThatDidNotFailIsNotRetryable() {
        PipelineRun run = run();
        PipelineRun done = finished(run, RunStatus.SUCCEEDED);
        RunUnit succeeded = pending(run).startIngest("i", T1).packed(T1)
                .startImport(UUID.randomUUID(), T1).succeed(T2);
        RunUnit partial = pending(run).startIngest("i", T1).packed(T1).startImport(UUID.randomUUID(), T1).partial(T2);
        RunUnit noChanges = pending(run).startIngest("i", T1).noChanges(T2);

        for (RunUnit unit : List.of(succeeded, partial, noChanges)) {
            assertThat(UnitRetryRules.check(done, unit, Optional.empty()))
                    .isEqualTo(UnitRetryEligibility.no(UnitRetryEligibility.UNIT_NOT_RETRYABLE));
        }
    }

    @Test
    void aRunThatIsStillActiveWinsOverAnIneligibleUnit() {
        PipelineRun run = run().start(T1);

        assertThat(UnitRetryRules.check(run, pending(run), Optional.empty()).code())
                .isEqualTo(UnitRetryEligibility.RUN_ACTIVE);
    }

    @Test
    void eligibilityRequiresACodeExactlyWhenNotAllowed() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new UnitRetryEligibility(false, null))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new UnitRetryEligibility(true, "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
