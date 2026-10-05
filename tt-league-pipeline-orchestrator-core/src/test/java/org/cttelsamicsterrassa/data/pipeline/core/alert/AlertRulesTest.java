package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.alert.AlertFixtures.NOW;
import static org.cttelsamicsterrassa.data.pipeline.core.alert.AlertFixtures.SETTINGS;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlertRulesTest {

    private static Set<AlertCondition> holding(AlertFacts facts) {
        return AlertRules.holding(facts, SETTINGS, NOW);
    }

    private static List<AlertKind> kinds(Set<AlertCondition> conditions) {
        return conditions.stream().map(AlertCondition::kind).toList();
    }

    private static AlertFacts withClosed(MatchDay closed, MatchDay... alertDays) {
        return AlertFixtures.facts(List.of(), List.of(closed), List.of(), List.of(alertDays), Map.of(), Map.of());
    }

    private static AlertFacts withRuns(PipelineSource source, PipelineRun... runs) {
        return AlertFixtures.facts(List.of(), List.of(), List.of(), List.of(), Map.of(source, List.of(runs)),
                Map.of());
    }

    private static AlertFacts withMatch(MatchDay day, MatchTracking match) {
        return AlertFixtures.facts(List.of(day), List.of(), List.of(match), List.of(), Map.of(),
                Map.of(day.key().source(), NOW.minusSeconds(3600)));
    }

    // MATCH_DAY_CLOSED

    @Test
    void closedDayInsideTheLookbackRaisesForEveryCloseReason() {
        for (CloseReason reason : CloseReason.values()) {
            MatchDay day = AlertFixtures.closed(
                    AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(3))), reason,
                    NOW.minus(Duration.ofHours(2)));

            Set<AlertCondition> result = holding(withClosed(day));

            assertThat(result).singleElement().satisfies(condition -> {
                assertThat(condition.kind()).isEqualTo(AlertKind.MATCH_DAY_CLOSED);
                assertThat(condition.conditionKey()).isEqualTo(day.id().toString());
                assertThat(condition.detail()).contains("Close reason: " + reason);
            });
        }
    }

    @Test
    void lookbackBoundaryIsInclusive() {
        MatchDay open = AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(3)));
        MatchDay onTheEdge = AlertFixtures.closed(open, CloseReason.MANUAL, NOW.minus(Duration.ofDays(1)));
        MatchDay justOutside = AlertFixtures.closed(open, CloseReason.MANUAL,
                NOW.minus(Duration.ofDays(1)).minusSeconds(1));

        assertThat(kinds(holding(withClosed(onTheEdge)))).containsExactly(AlertKind.MATCH_DAY_CLOSED);
        assertThat(holding(withClosed(justOutside))).isEmpty();
    }

    @Test
    void anActiveAlertKeepsHoldingPastTheLookbackWhileTheDayStaysClosed() {
        MatchDay open = AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(9)));
        MatchDay oldClosed = AlertFixtures.closed(open, CloseReason.ALL_RESOLVED, NOW.minus(Duration.ofDays(5)));

        assertThat(kinds(holding(withActiveAlertDays(oldClosed)))).containsExactly(AlertKind.MATCH_DAY_CLOSED);
    }

    @Test
    void reopeningTheDayClearsTheCondition() {
        MatchDay open = AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(9)));
        MatchDay reopened = AlertFixtures.closed(open, CloseReason.MANUAL, NOW.minus(Duration.ofHours(3)))
                .reopen(NOW.minus(Duration.ofHours(1)));

        assertThat(holding(withActiveAlertDays(reopened))).isEmpty();
    }

    private static AlertFacts withActiveAlertDays(MatchDay day) {
        return AlertFixtures.facts(List.of(), List.of(), List.of(), List.of(day), Map.of(), Map.of());
    }

    // RUN_FAILURES

    @Test
    void twoFailedRunsRaiseAndTheTextCarriesCodesButNotMessages() {
        PipelineRun newest = AlertFixtures.failedRun(PipelineSource.BCNESA, NOW.minusSeconds(60), "IMPORT_FAILED");
        PipelineRun older = AlertFixtures.failedRun(PipelineSource.BCNESA, NOW.minusSeconds(3600), "INGEST_FAILED");

        Set<AlertCondition> result = holding(withRuns(PipelineSource.BCNESA, newest, older));

        assertThat(result).singleElement().satisfies(condition -> {
            assertThat(condition.kind()).isEqualTo(AlertKind.RUN_FAILURES);
            assertThat(condition.conditionKey()).isEqualTo("BCNESA");
            assertThat(condition.source()).isEqualTo(PipelineSource.BCNESA);
            assertThat(condition.detail()).contains("IMPORT_FAILED", "INGEST_FAILED", newest.id().toString());
            assertThat(condition.detail()).doesNotContain("secret message");
        });
    }

    @Test
    void aSingleFailureDoesNotRaise() {
        PipelineRun failed = AlertFixtures.failedRun(PipelineSource.FCTT, NOW.minusSeconds(60), "IMPORT_FAILED");
        PipelineRun ok = AlertFixtures.succeededRun(PipelineSource.FCTT, NOW.minusSeconds(3600));

        assertThat(holding(withRuns(PipelineSource.FCTT, failed))).isEmpty();
        assertThat(holding(withRuns(PipelineSource.FCTT, failed, ok))).isEmpty();
    }

    @Test
    void aPartialRunBreaksTheStreak() {
        PipelineRun failed = AlertFixtures.failedRun(PipelineSource.FCTT, NOW.minusSeconds(60), "IMPORT_FAILED");
        PipelineRun partial = AlertFixtures.partialRun(PipelineSource.FCTT, NOW.minusSeconds(3600));

        assertThat(holding(withRuns(PipelineSource.FCTT, failed, partial))).isEmpty();
    }

    @Test
    void aNewerSuccessClearsTheCondition() {
        PipelineRun ok = AlertFixtures.succeededRun(PipelineSource.FCTT, NOW.minusSeconds(10));
        PipelineRun failed = AlertFixtures.failedRun(PipelineSource.FCTT, NOW.minusSeconds(3600), "X");

        assertThat(holding(withRuns(PipelineSource.FCTT, ok, failed))).isEmpty();
    }

    // MATCH_UNREPORTED

    @Test
    void unreportedThresholdIsExact() {
        MatchDay day = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(5)));
        Instant exactly = NOW.minus(Duration.ofHours(48));

        assertThat(kinds(holding(withMatch(day,
                AlertFixtures.match(day, TrackedMatchStatus.OVERDUE, exactly)))))
                .containsExactly(AlertKind.MATCH_UNREPORTED);
        assertThat(holding(withMatch(day,
                AlertFixtures.match(day, TrackedMatchStatus.OVERDUE, exactly.plusSeconds(1))))).isEmpty();
    }

    @Test
    void awaitedStatusesRaiseButResolvedOnesDoNot() {
        MatchDay day = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(5)));
        Instant old = NOW.minus(Duration.ofDays(3));

        for (TrackedMatchStatus status : List.of(TrackedMatchStatus.SCHEDULED, TrackedMatchStatus.AWAITING_RESULT,
                TrackedMatchStatus.OVERDUE)) {
            assertThat(holding(withMatch(day, AlertFixtures.match(day, status, old)))).hasSize(1);
        }
        assertThat(holding(withMatch(day, AlertFixtures.match(day, TrackedMatchStatus.REPORTED, old)))).isEmpty();
        assertThat(holding(withMatch(day, AlertFixtures.match(day, TrackedMatchStatus.POSTPONED, old)))).isEmpty();
    }

    @Test
    void ignoredAndUndatedMatchesAreExcluded() {
        MatchDay day = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(5)));
        MatchTracking overdue = AlertFixtures.match(day, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3)));
        MatchTracking undated = MatchTracking.first(overdue.matchId(), day.id(), TrackedMatchStatus.SCHEDULED, null,
                "H", "A", NOW, null, null);

        assertThat(holding(withMatch(day, overdue.ignore("ana", NOW)))).isEmpty();
        assertThat(holding(withMatch(day, undated))).isEmpty();
    }

    @Test
    void matchesOfADayThatIsNoLongerOpenAreExcluded() {
        MatchDay day = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(5)));
        MatchTracking overdue = AlertFixtures.match(day, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3)));

        AlertFacts facts = AlertFixtures.facts(List.of(), List.of(), List.of(overdue), List.of(), Map.of(), Map.of());

        assertThat(holding(facts)).isEmpty();
    }

    @Test
    void unreportedTextNamesTheMatchAndTheDayInUtc() {
        MatchDay day = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(5)));
        MatchTracking match = AlertFixtures.match(day, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3)));

        AlertCondition condition = holding(withMatch(day, match)).iterator().next();

        assertThat(condition.conditionKey()).isEqualTo(match.matchId().toString());
        assertThat(condition.title()).contains("Home - Away", "TERCERA group 1 1a Fase round 1");
        assertThat(condition.detail()).contains("2026-10-02T12:00:00Z", "Competition: TERCERA", "Status: OVERDUE");
    }

    // NO_RECENT_SUCCESS

    @Test
    void windowStartsAtOpenedAtSoANewDayDoesNotRaise() {
        MatchDay fresh = AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofHours(23)));
        AlertFacts facts = AlertFixtures.facts(List.of(fresh), List.of(), List.of(), List.of(), Map.of(), Map.of());

        assertThat(holding(facts)).isEmpty();
    }

    @Test
    void noSuccessInTheWindowRaisesAtTheBoundary() {
        MatchDay open = AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofDays(3)));
        Instant exactly = NOW.minus(Duration.ofHours(24));

        assertThat(kinds(holding(successFacts(open, exactly)))).containsExactly(AlertKind.NO_RECENT_SUCCESS);
        assertThat(holding(successFacts(open, exactly.plusSeconds(1)))).isEmpty();
    }

    @Test
    void neverSucceededRaisesOnceTheWindowSinceOpenedAtPassed() {
        MatchDay open = AlertFixtures.openDay(PipelineSource.FCTT, NOW.minus(Duration.ofHours(25)));
        AlertFacts facts = AlertFixtures.facts(List.of(open), List.of(), List.of(), List.of(), Map.of(), Map.of());

        AlertCondition condition = holding(facts).iterator().next();

        assertThat(condition.kind()).isEqualTo(AlertKind.NO_RECENT_SUCCESS);
        assertThat(condition.conditionKey()).isEqualTo("FCTT");
        assertThat(condition.detail()).contains("never");
    }

    @Test
    void noOpenDayOfTheSourceClearsTheCondition() {
        MatchDay other = AlertFixtures.openDay(PipelineSource.RFETM, NOW.minus(Duration.ofDays(3)));
        AlertFacts facts = AlertFixtures.facts(List.of(other), List.of(), List.of(), List.of(), Map.of(),
                Map.of(PipelineSource.RFETM, NOW.minusSeconds(60)));

        assertThat(holding(facts)).isEmpty();
        assertThat(holding(AlertFixtures.empty())).isEmpty();
    }

    private static AlertFacts successFacts(MatchDay open, Instant success) {
        return AlertFixtures.facts(List.of(open), List.of(), List.of(), List.of(), Map.of(),
                Map.of(PipelineSource.FCTT, success));
    }
}
