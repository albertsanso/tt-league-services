package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.alert.AlertFixtures.NOW;
import static org.cttelsamicsterrassa.data.pipeline.core.alert.AlertFixtures.SETTINGS;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.ActiveAlertExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryAlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingNotifier;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlertEvaluatorTest {

    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryRunUnitRepository units = new InMemoryRunUnitRepository(runs);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakeRunClock clock = new FakeRunClock(NOW);
    private final AlertEvaluator evaluator =
            new AlertEvaluator(alerts, matchDays, runs, units, notifier, clock, SETTINGS);

    private MatchDay store(MatchDay day) {
        matchDays.apply(new MatchDayChangeSet(List.of(day), List.of(), Set.of(), List.of()));
        return matchDays.findById(day.id()).orElseThrow();
    }

    private void store(MatchDay day, MatchTracking match) {
        matchDays.apply(new MatchDayChangeSet(List.of(day), List.of(match), Set.of(), List.of()));
    }

    private void failTwice(PipelineSource source) {
        runs.create(AlertFixtures.failedRun(source, NOW.minusSeconds(3600), "INGEST_FAILED"));
        runs.create(AlertFixtures.failedRun(source, NOW.minusSeconds(60), "IMPORT_FAILED"));
    }

    @Test
    void nothingHoldingSendsNothing() {
        EvaluationOutcome outcome = evaluator.evaluate();

        assertThat(outcome.isEmpty()).isTrue();
        assertThat(notifier.attempts()).isZero();
    }

    @Test
    void aConditionIsSentOncePerPassSequenceUntilItClears() {
        failTwice(PipelineSource.FCTT);

        EvaluationOutcome first = evaluator.evaluate();
        EvaluationOutcome second = evaluator.evaluate();
        runs.create(AlertFixtures.failedRun(PipelineSource.FCTT, NOW.minusSeconds(10), "IMPORT_FAILED"));
        EvaluationOutcome third = evaluator.evaluate();

        assertThat(first).isEqualTo(new EvaluationOutcome(1, 0, 1, 0));
        assertThat(second.isEmpty()).isTrue();
        assertThat(third.isEmpty()).isTrue();
        assertThat(notifier.sent()).hasSize(1);
        assertThat(notifier.sent().get(0).subject()).isEqualTo("FCTT: two consecutive runs failed");
        assertThat(alerts.findActive()).singleElement().satisfies(alert -> {
            assertThat(alert.notifiedAt()).isEqualTo(NOW);
            assertThat(alert.notifyAttempts()).isEqualTo(1);
        });
    }

    @Test
    void severalNewAlertsShareOneEmailOrderedByKind() {
        failTwice(PipelineSource.FCTT);
        MatchDay open = store(AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(3))));
        store(AlertFixtures.closed(AlertFixtures.openDay(PipelineSource.BCNESA, NOW.minus(Duration.ofDays(2))),
                CloseReason.MANUAL, NOW.minus(Duration.ofHours(1))));

        EvaluationOutcome outcome = evaluator.evaluate();

        assertThat(open).isNotNull();
        assertThat(outcome.raised()).isEqualTo(3);
        assertThat(outcome.sent()).isEqualTo(3);
        assertThat(notifier.sent()).singleElement().satisfies(notification -> {
            assertThat(notification.subject()).isEqualTo("3 pipeline alerts");
            assertThat(notification.body().indexOf("match day closed"))
                    .isLessThan(notification.body().indexOf("two consecutive runs failed"))
                    .isLessThan(notification.body().indexOf("no successful run"));
        });
    }

    @Test
    void aFailedSendKeepsNotifiedAtNullAndTheNextPassResends() {
        failTwice(PipelineSource.FCTT);
        notifier.failWith(true);

        EvaluationOutcome failed = evaluator.evaluate();

        assertThat(failed).isEqualTo(new EvaluationOutcome(1, 0, 0, 1));
        assertThat(alerts.findActive()).singleElement().satisfies(alert -> {
            assertThat(alert.notifiedAt()).isNull();
            assertThat(alert.notifyAttempts()).isEqualTo(1);
            assertThat(alert.lastFailure()).isEqualTo("NotificationException");
        });

        notifier.failWith(false);
        EvaluationOutcome retried = evaluator.evaluate();

        assertThat(retried).isEqualTo(new EvaluationOutcome(0, 0, 1, 0));
        assertThat(notifier.sent()).hasSize(1);
        assertThat(alerts.findActive()).singleElement().satisfies(alert -> {
            assertThat(alert.notifiedAt()).isNotNull();
            assertThat(alert.lastFailure()).isNull();
            assertThat(alert.notifyAttempts()).isEqualTo(2);
        });
    }

    @Test
    void aConflictOnRaiseIsSkippedWithoutSending() {
        failTwice(PipelineSource.FCTT);
        InMemoryAlertRepository racing = new InMemoryAlertRepository() {
            @Override
            public synchronized Alert raise(Alert alert) {
                throw new ActiveAlertExistsException(alert.kind(), alert.conditionKey());
            }
        };
        AlertEvaluator racingEvaluator = new AlertEvaluator(racing, matchDays, runs, units, notifier, clock, SETTINGS);

        EvaluationOutcome outcome = racingEvaluator.evaluate();

        assertThat(outcome.isEmpty()).isTrue();
        assertThat(notifier.attempts()).isZero();
    }

    @Test
    void clearingThenRaisingAgainSendsAgain() {
        failTwice(PipelineSource.FCTT);
        evaluator.evaluate();
        runs.create(AlertFixtures.succeededRun(PipelineSource.FCTT, NOW.plusSeconds(5)));
        clock.advance(Duration.ofSeconds(10));

        EvaluationOutcome cleared = evaluator.evaluate();

        assertThat(cleared).isEqualTo(new EvaluationOutcome(0, 1, 0, 0));
        assertThat(alerts.findActive()).isEmpty();
        assertThat(notifier.sent()).hasSize(1);

        runs.create(AlertFixtures.failedRun(PipelineSource.FCTT, NOW.plusSeconds(20), "IMPORT_FAILED"));
        runs.create(AlertFixtures.failedRun(PipelineSource.FCTT, NOW.plusSeconds(30), "IMPORT_FAILED"));
        clock.advance(Duration.ofSeconds(60));
        EvaluationOutcome again = evaluator.evaluate();

        assertThat(again).isEqualTo(new EvaluationOutcome(1, 0, 1, 0));
        assertThat(notifier.sent()).hasSize(2);
        assertThat(alerts.all()).hasSize(2);
    }

    @Test
    void aClosedDayStaysAlertedPastTheLookbackUntilItIsReopened() {
        MatchDay closed = store(AlertFixtures.closed(
                AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(3))), CloseReason.ALL_RESOLVED,
                NOW.minus(Duration.ofHours(2))));
        evaluator.evaluate();

        clock.advance(Duration.ofDays(3));
        assertThat(evaluator.evaluate().isEmpty()).isTrue();
        assertThat(alerts.findActive()).hasSize(1);

        runs.create(AlertFixtures.succeededRun(PipelineSource.FCTT, clock.now().minusSeconds(60)));
        MatchDay reopened = closed.reopen(clock.now());
        matchDays.apply(new MatchDayChangeSet(List.of(reopened), List.of(), Set.of(), List.of()));
        EvaluationOutcome outcome = evaluator.evaluate();

        assertThat(outcome.cleared()).isEqualTo(1);
        assertThat(notifier.sent()).hasSize(1);
    }

    @Test
    void unreportedMatchAlertsAndClearsWhenReported() {
        MatchDay day = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(5)));
        MatchTracking match = AlertFixtures.match(day, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3)));
        store(day, match);
        runs.create(AlertFixtures.succeededRun(PipelineSource.RFETM, NOW.minusSeconds(60)));

        assertThat(evaluator.evaluate().raised()).isEqualTo(1);

        MatchDay stored = matchDays.findById(day.id()).orElseThrow();
        MatchTracking storedMatch = matchDays.findMatch(match.matchId()).orElseThrow();
        MatchTracking reported = storedMatch.observe(TrackedMatchStatus.REPORTED, storedMatch.matchDateTime(), "Home",
                "Away", NOW, NOW, null);
        matchDays.apply(new MatchDayChangeSet(List.of(stored), List.of(reported), Set.of(), List.of()));

        assertThat(evaluator.evaluate().cleared()).isEqualTo(1);
    }

    @Test
    void textsNeverContainTheRunErrorMessage() {
        failTwice(PipelineSource.FCTT);

        evaluator.evaluate();

        assertThat(notifier.sent()).singleElement().satisfies(notification -> {
            assertThat(notification.body()).contains("IMPORT_FAILED").doesNotContain("secret message");
            assertThat(alerts.findActive().get(0).detail()).doesNotContain("secret message");
        });
    }

    @Test
    void successfulRunsKeepTheNoRecentSuccessAlertAway() {
        store(AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(3))));
        runs.create(AlertFixtures.noChangesRun(PipelineSource.FCTT, NOW.minusSeconds(60)));

        assertThat(evaluator.evaluate().isEmpty()).isTrue();

        clock.advance(Duration.ofHours(25));
        EvaluationOutcome outcome = evaluator.evaluate();

        assertThat(outcome.raised()).isEqualTo(1);
        assertThat(notifier.sent().get(0).subject()).isEqualTo("FCTT: no successful run during an open match day");
    }
}
