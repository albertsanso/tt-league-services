package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.alert.AlertFixtures.NOW;
import static org.cttelsamicsterrassa.data.pipeline.core.alert.AlertFixtures.SETTINGS;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryAlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingNotifier;
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

/** The UNIT_FAILURES condition: the same unit failed in its two newest finished occurrences. */
class AlertUnitFailuresTest {

    private static final ScopeFilter G1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
    private static final ScopeFilter G2 = new ScopeFilter("SENIOR", "G2", null, null, null, List.of(3));
    private static final String KEY1 = UnitKey.of(G1);
    private static final String KEY2 = UnitKey.of(G2);
    private static final PipelineSource SOURCE = PipelineSource.BCNESA;
    private static final RunError ERROR = new RunError("INGEST_FAILED", "secret message");

    private static RunUnit unit(ScopeFilter filter, UnitOutcome outcome, Instant finishedAt) {
        RunUnit pending = RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 0, UnitKey.of(filter),
                "SENIOR " + filter.group() + " · J3", new RunScope(List.of(filter)));
        Instant started = finishedAt.minusSeconds(30);
        return switch (outcome) {
            case FAILED -> pending.startIngest("i", started).fail(ERROR, finishedAt);
            case SUCCEEDED -> pending.startIngest("i", started).packed(started)
                    .startImport(UUID.randomUUID(), started).succeed(finishedAt);
            case NO_CHANGES -> pending.startIngest("i", started).noChanges(finishedAt);
            case SKIPPED -> pending.skip(new RunError("UNIT_SKIPPED", "skipped"), finishedAt);
        };
    }

    private enum UnitOutcome { FAILED, SUCCEEDED, NO_CHANGES, SKIPPED }

    private static AlertFacts facts(Map<String, List<RunUnit>> unitsOfSource) {
        return new AlertFacts(List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(),
                Map.of(SOURCE, unitsOfSource));
    }

    private static Set<AlertCondition> holding(AlertFacts facts, AlertSettings settings) {
        return AlertRules.holding(facts, settings, NOW);
    }

    private static List<RunUnit> twoFailures(ScopeFilter filter) {
        return List.of(unit(filter, UnitOutcome.FAILED, NOW.minusSeconds(60)),
                unit(filter, UnitOutcome.FAILED, NOW.minusSeconds(3600)));
    }

    @Test
    void theSameUnitFailingInItsTwoNewestOccurrencesRaises() {
        Set<AlertCondition> result = holding(facts(Map.of(KEY1, twoFailures(G1))), SETTINGS);

        assertThat(result).singleElement().satisfies(condition -> {
            assertThat(condition.kind()).isEqualTo(AlertKind.UNIT_FAILURES);
            assertThat(condition.conditionKey()).isEqualTo("BCNESA:" + KEY1);
            assertThat(condition.source()).isEqualTo(SOURCE);
            assertThat(condition.season()).isNull();
            assertThat(condition.title()).isEqualTo("BCNESA: unit SENIOR G1 · J3 failed twice in a row");
            assertThat(condition.detail()).contains("Error code: INGEST_FAILED").doesNotContain("secret message");
        });
    }

    @Test
    void aSuccessBetweenTheFailuresOrAsTheNewestClearsTheCondition() {
        List<RunUnit> recovered = List.of(unit(G1, UnitOutcome.SUCCEEDED, NOW.minusSeconds(60)),
                unit(G1, UnitOutcome.FAILED, NOW.minusSeconds(3600)));
        List<RunUnit> olderSuccess = List.of(unit(G1, UnitOutcome.FAILED, NOW.minusSeconds(60)),
                unit(G1, UnitOutcome.NO_CHANGES, NOW.minusSeconds(3600)));

        assertThat(holding(facts(Map.of(KEY1, recovered)), SETTINGS)).isEmpty();
        assertThat(holding(facts(Map.of(KEY1, olderSuccess)), SETTINGS)).isEmpty();
    }

    @Test
    void aSingleOccurrenceIsNotEnough() {
        List<RunUnit> single = List.of(unit(G1, UnitOutcome.FAILED, NOW.minusSeconds(60)));

        assertThat(holding(facts(Map.of(KEY1, single)), SETTINGS)).isEmpty();
    }

    @Test
    void skippedUnitsAreNeverFailures() {
        List<RunUnit> skipped = List.of(unit(G1, UnitOutcome.SKIPPED, NOW.minusSeconds(60)),
                unit(G1, UnitOutcome.SKIPPED, NOW.minusSeconds(3600)));
        List<RunUnit> oneSkipped = List.of(unit(G1, UnitOutcome.FAILED, NOW.minusSeconds(60)),
                unit(G1, UnitOutcome.SKIPPED, NOW.minusSeconds(3600)));

        assertThat(holding(facts(Map.of(KEY1, skipped)), SETTINGS)).isEmpty();
        assertThat(holding(facts(Map.of(KEY1, oneSkipped)), SETTINGS)).isEmpty();
    }

    @Test
    void eachUnitKeyIsEvaluatedOnItsOwn() {
        Set<AlertCondition> result = holding(facts(Map.of(KEY1, twoFailures(G1), KEY2, twoFailures(G2))), SETTINGS);

        assertThat(result).extracting(AlertCondition::conditionKey)
                .containsExactlyInAnyOrder("BCNESA:" + KEY1, "BCNESA:" + KEY2);
    }

    @Test
    void theUnitKeySettingLimitsTheAlertsToTheChosenUnits() {
        AlertSettings only1 = new AlertSettings(Duration.ofHours(48), Duration.ofHours(24), Duration.ofDays(1),
                Set.of(KEY1));
        AlertFacts both = facts(Map.of(KEY1, twoFailures(G1), KEY2, twoFailures(G2)));

        assertThat(holding(both, only1)).extracting(AlertCondition::conditionKey).containsExactly("BCNESA:" + KEY1);
        assertThat(holding(both, SETTINGS)).hasSize(2);
        assertThat(SETTINGS.unitKeys()).isEmpty();
    }

    @Test
    void factsWithoutUnitHistoryRaiseNothing() {
        assertThat(holding(AlertFixtures.empty(), SETTINGS)).isEmpty();
    }

    @Test
    void theEvaluatorReadsTheNewestFinishedUnitsOfEachSourceAndRaisesAndClears() {
        InMemoryAlertRepository alerts = new InMemoryAlertRepository();
        InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
        InMemoryRunUnitRepository units = new InMemoryRunUnitRepository(runs);
        RecordingNotifier notifier = new RecordingNotifier();
        AlertEvaluator evaluator = new AlertEvaluator(alerts, new InMemoryMatchDayRepository(), runs, units, notifier,
                new FakeRunClock(NOW), SETTINGS);
        failUnit(runs, units, NOW.minusSeconds(3600), UnitOutcome.FAILED);
        failUnit(runs, units, NOW.minusSeconds(60), UnitOutcome.FAILED);

        EvaluationOutcome raised = evaluator.evaluate();

        assertThat(raised.raised()).isEqualTo(1);
        assertThat(notifier.sent()).singleElement()
                .satisfies(notification -> assertThat(notification.subject()).contains("failed twice in a row"));
        assertThat(alerts.findActive()).singleElement()
                .satisfies(alert -> assertThat(alert.kind()).isEqualTo(AlertKind.UNIT_FAILURES));

        failUnit(runs, units, NOW.minusSeconds(10), UnitOutcome.SUCCEEDED);
        EvaluationOutcome cleared = evaluator.evaluate();

        assertThat(cleared.cleared()).isEqualTo(1);
        assertThat(alerts.findActive()).isEmpty();
    }

    private void failUnit(InMemoryPipelineRunRepository runs, InMemoryRunUnitRepository units, Instant finishedAt,
            UnitOutcome outcome) {
        PipelineRun queued = runs.create(PipelineRun.queue(UUID.randomUUID(), SOURCE, "2026-2027",
                new RunScope(List.of(G1)), false, RunTrigger.SCHEDULED, "system:scheduler", null,
                finishedAt.minusSeconds(120)));
        RunUnit planned = units.addAll(List.of(RunUnit.plan(UUID.randomUUID(), queued.id(), 0, KEY1,
                "SENIOR G1 · J3", new RunScope(List.of(G1))))).get(0);
        Instant started = finishedAt.minusSeconds(30);
        if (outcome == UnitOutcome.FAILED) {
            units.update(planned.startIngest("i", started).fail(ERROR, finishedAt));
            // a unit that failed next to a successful one leaves the run PARTIAL: only the unit alert can fire
            runs.update(queued.start(started).finish(RunStatus.PARTIAL, null, finishedAt));
        } else {
            units.update(planned.startIngest("i", started).noChanges(finishedAt));
            runs.update(queued.start(started).finish(RunStatus.NO_CHANGES, null, finishedAt));
        }
    }
}
