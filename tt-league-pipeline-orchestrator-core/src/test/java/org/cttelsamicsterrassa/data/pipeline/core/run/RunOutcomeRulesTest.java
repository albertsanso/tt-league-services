package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunOutcomeRulesTest {

    private static final Instant T = Instant.parse("2026-10-04T10:00:00Z");
    private static final UUID RUN = UUID.randomUUID();

    private static RunUnit unit(int ordinal, String group) {
        ScopeFilter filter = new ScopeFilter("SENIOR", group, null, null, null, List.of(1));
        return RunUnit.plan(UUID.randomUUID(), RUN, ordinal, UnitKey.of(filter), "SENIOR " + group,
                new RunScope(List.of(filter)));
    }

    private static RunUnit succeeded(int ordinal) {
        return unit(ordinal, "G" + ordinal).startIngest("i", T).packed(T).startImport(UUID.randomUUID(), T)
                .succeed(T.plusSeconds(1));
    }

    private static RunUnit partial(int ordinal) {
        return unit(ordinal, "G" + ordinal).startIngest("i", T).packed(T).startImport(UUID.randomUUID(), T)
                .partial(T.plusSeconds(1));
    }

    private static RunUnit noChanges(int ordinal) {
        return unit(ordinal, "G" + ordinal).startIngest("i", T).noChanges(T.plusSeconds(1));
    }

    private static RunUnit failed(int ordinal, String code) {
        return unit(ordinal, "G" + ordinal).startIngest("i", T).fail(new RunError(code, "msg " + code),
                T.plusSeconds(1));
    }

    private static RunUnit skipped(int ordinal) {
        return unit(ordinal, "G" + ordinal).skip(new RunError("UNIT_SKIPPED", "skipped after X"), T.plusSeconds(1));
    }

    @Test
    void everyUnitSucceededIsSucceeded() {
        RunOutcomeRules.Outcome outcome = RunOutcomeRules.derive(List.of(succeeded(0), succeeded(1)));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(outcome.error()).isNull();
    }

    @Test
    void everyUnitWithoutChangesIsNoChanges() {
        assertThat(RunOutcomeRules.derive(List.of(noChanges(0), noChanges(1))).status())
                .isEqualTo(RunStatus.NO_CHANGES);
        assertThat(RunOutcomeRules.derive(List.of(noChanges(0))).status()).isEqualTo(RunStatus.NO_CHANGES);
    }

    @Test
    void succeededAndNoChangesTogetherAreSucceeded() {
        assertThat(RunOutcomeRules.derive(List.of(noChanges(0), succeeded(1), noChanges(2))).status())
                .isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    void aPartialUnitMakesTheRunPartial() {
        assertThat(RunOutcomeRules.derive(List.of(succeeded(0), partial(1))).status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(RunOutcomeRules.derive(List.of(partial(0))).status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(RunOutcomeRules.derive(List.of(partial(0), failed(1, "IMPORT_FAILED"))).status())
                .isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void failedOrSkippedNextToSuccessfulUnitsIsPartial() {
        RunOutcomeRules.Outcome mixed = RunOutcomeRules.derive(List.of(succeeded(0), failed(1, "INGEST_FAILED")));
        assertThat(mixed.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(mixed.error()).isNull();

        assertThat(RunOutcomeRules.derive(List.of(noChanges(0), failed(1, "INGEST_FAILED"))).status())
                .isEqualTo(RunStatus.PARTIAL);
        assertThat(RunOutcomeRules.derive(List.of(succeeded(0), failed(1, "INGEST_UNAVAILABLE"), skipped(2))).status())
                .isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void aSingleFailedUnitPassesItsErrorThroughUnchanged() {
        RunUnit failed = failed(0, "IMPORT_FAILED");

        RunOutcomeRules.Outcome outcome = RunOutcomeRules.derive(List.of(failed));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.error()).isEqualTo(failed.error());
    }

    @Test
    void severalFailedUnitsReportTheFirstFailedCodeAndTheCodeList() {
        RunOutcomeRules.Outcome outcome = RunOutcomeRules.derive(
                List.of(failed(0, "INGEST_FAILED"), failed(1, "IMPORT_FAILED"), failed(2, "INGEST_FAILED")));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.error().code()).isEqualTo("INGEST_FAILED");
        assertThat(outcome.error().message()).isEqualTo("3 of 3 units failed: INGEST_FAILED, IMPORT_FAILED");
    }

    @Test
    void failedThenSkippedUnitsFailTheRunWithTheFailedUnitsCode() {
        RunOutcomeRules.Outcome outcome =
                RunOutcomeRules.derive(List.of(failed(0, "INGEST_UNAVAILABLE"), skipped(1), skipped(2)));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.error().code()).isEqualTo("INGEST_UNAVAILABLE");
        assertThat(outcome.error().message()).contains("3 of 3 units failed").contains("UNIT_SKIPPED");
    }

    @Test
    void aSkippedUnitBeforeTheFailedOneDoesNotHideTheFailedCode() {
        RunOutcomeRules.Outcome outcome = RunOutcomeRules.derive(List.of(skipped(0), failed(1, "INGEST_FAILED")));

        assertThat(outcome.error().code()).isEqualTo("INGEST_FAILED");
    }

    @Test
    void anActiveUnitIsRejected() {
        RunUnit running = unit(1, "G1").startIngest("i", T);

        assertThatThrownBy(() -> RunOutcomeRules.derive(List.of(succeeded(0), running)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUNNING_INGEST");
        assertThatThrownBy(() -> RunOutcomeRules.derive(List.of(unit(0, "G0"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noUnitsIsRejected() {
        assertThatThrownBy(() -> RunOutcomeRules.derive(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
