package org.cttelsamicsterrassa.data.pipeline.core.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.polling.PollingFixtures.ZONE;
import static org.cttelsamicsterrassa.data.pipeline.core.polling.PollingFixtures.at;
import static org.cttelsamicsterrassa.data.pipeline.core.polling.PollingFixtures.match;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class PollingPolicyTest {

    private static final Instant NOW = at("2026-10-04T12:00");
    private static final Instant LAST_RUN = at("2026-10-04T11:00");

    private final PollingPolicy policy = new PollingPolicy();
    private final PollingSettings settings = PollingSettings.defaults();

    private PollDecision decide(PollState state, Instant now, MatchTracking... matches) {
        return policy.decide(state, List.of(matches), settings, now, ZONE);
    }

    private PollDecision decide(MatchTracking... matches) {
        return decide(new PollState(null, 0, LAST_RUN), NOW, matches);
    }

    @Test
    void matchDayStartsTwoHoursAfterTheFirstStartInclusive() {
        MatchTracking first = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00");
        MatchTracking later = match(TrackedMatchStatus.SCHEDULED, "2026-10-04T20:00");

        PollDecision atBoundary = decide(first, later);

        assertThat(atBoundary.level()).isEqualTo(PolicyLevel.MATCH_DAY);
        assertThat(atBoundary.baseInterval()).isEqualTo(Duration.ofHours(2));
        assertThat(atBoundary.nextRunAt()).isEqualTo(LAST_RUN.plus(Duration.ofHours(2)));

        PollDecision oneMinuteBefore = decide(new PollState(null, 0, LAST_RUN), at("2026-10-04T11:59"), first, later);
        assertThat(oneMinuteBefore.level()).isEqualTo(PolicyLevel.OPEN);
    }

    @Test
    void openBeforeTheOffsetIsCappedAtTheFirstStartPlusOffset() {
        MatchTracking today = match(TrackedMatchStatus.SCHEDULED, "2026-10-04T14:00");
        Instant now = at("2026-10-04T08:00");
        PollState state = new PollState(null, 0, at("2026-10-04T07:00"));

        PollDecision decision = decide(state, now, today);

        assertThat(decision.level()).isEqualTo(PolicyLevel.OPEN);
        assertThat(decision.nextRunAt()).isEqualTo(at("2026-10-04T16:00"));
    }

    @Test
    void openWithAFutureDayIsCappedAtTheNextStartPlusOffset() {
        MatchTracking tomorrow = match(TrackedMatchStatus.SCHEDULED, "2026-10-05T09:00");
        Instant now = at("2026-10-04T14:00");

        PollDecision decision = decide(new PollState(null, 0, at("2026-10-04T14:00")), now, tomorrow);

        assertThat(decision.level()).isEqualTo(PolicyLevel.OPEN);
        assertThat(decision.nextRunAt()).isEqualTo(at("2026-10-05T11:00"));
    }

    @Test
    void openFallsBackToTheIntervalWhenTheCapIsLater() {
        MatchTracking future = match(TrackedMatchStatus.SCHEDULED, "2026-10-20T09:00");

        PollDecision decision = decide(future);

        assertThat(decision.level()).isEqualTo(PolicyLevel.OPEN);
        assertThat(decision.nextRunAt()).isEqualTo(LAST_RUN.plus(Duration.ofHours(24)));
    }

    @Test
    void undatedMatchesAreOpen() {
        assertThat(decide(match(TrackedMatchStatus.SCHEDULED, (String) null)).level()).isEqualTo(PolicyLevel.OPEN);
    }

    @Test
    void dayAfterAndTheNextSixDays() {
        assertThat(decide(match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-03T20:00")).level())
                .isEqualTo(PolicyLevel.DAY_AFTER);
        assertThat(decide(match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-02T20:00")).level())
                .isEqualTo(PolicyLevel.DAYS_2_TO_7);
        assertThat(decide(match(TrackedMatchStatus.AWAITING_RESULT, "2026-09-27T20:00")).level())
                .isEqualTo(PolicyLevel.DAYS_2_TO_7);
        assertThat(decide(match(TrackedMatchStatus.AWAITING_RESULT, "2026-09-26T20:00")).level())
                .isEqualTo(PolicyLevel.OVERDUE);
        assertThat(decide(match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-03T20:00")).baseInterval())
                .isEqualTo(Duration.ofHours(3));
        assertThat(decide(match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-02T20:00")).baseInterval())
                .isEqualTo(Duration.ofHours(12));
    }

    @Test
    void overdueMatchesPollDailyUntilTheStopLimit() {
        PollDecision overdue = decide(match(TrackedMatchStatus.OVERDUE, "2026-09-20T20:00"));
        assertThat(overdue.level()).isEqualTo(PolicyLevel.OVERDUE);
        assertThat(overdue.baseInterval()).isEqualTo(Duration.ofHours(24));

        assertThat(decide(match(TrackedMatchStatus.OVERDUE, "2026-09-13T20:00")).level())
                .isEqualTo(PolicyLevel.OVERDUE);
        PollDecision stopped = decide(match(TrackedMatchStatus.OVERDUE, "2026-09-12T20:00"));
        assertThat(stopped.level()).isEqualTo(PolicyLevel.STOPPED);
        assertThat(stopped.stopReason()).isEqualTo(PollDecision.OVERDUE_LIMIT);
        assertThat(stopped.nextRunAt()).isNull();
        assertThat(stopped.effectiveInterval()).isNull();
    }

    @Test
    void aUnitIsPolledOnceBeforeItStops() {
        PollDecision decision = decide(new PollState(null, 0, null), NOW,
                match(TrackedMatchStatus.OVERDUE, "2026-08-01T20:00"));

        assertThat(decision.level()).isEqualTo(PolicyLevel.OVERDUE);
        assertThat(decision.nextRunAt()).isEqualTo(NOW);
    }

    @Test
    void postponedMatchesFollowTheirDateButNeverStop() {
        PollDecision decision = decide(match(TrackedMatchStatus.POSTPONED, "2026-08-01T20:00"));

        assertThat(decision.level()).isEqualTo(PolicyLevel.OVERDUE);
    }

    @Test
    void theMostUrgentLevelWins() {
        PollDecision decision = decide(
                match(TrackedMatchStatus.OVERDUE, "2026-09-20T20:00"),
                match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-02T20:00"),
                match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-03T20:00"));

        assertThat(decision.level()).isEqualTo(PolicyLevel.DAY_AFTER);
    }

    @Test
    void aStoppedMatchDoesNotHideALiveOne() {
        PollDecision decision = decide(
                match(TrackedMatchStatus.OVERDUE, "2026-08-01T20:00"),
                match(TrackedMatchStatus.SCHEDULED, "2026-10-20T20:00"));

        assertThat(decision.level()).isEqualTo(PolicyLevel.OPEN);
    }

    @Test
    void reportedAndIgnoredMatchesAreNotCandidates() {
        MatchTracking ignored = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00").ignore("ops", NOW);

        PollDecision decision = decide(match(TrackedMatchStatus.REPORTED, "2026-10-04T10:00"), ignored);

        assertThat(decision.level()).isEqualTo(PolicyLevel.OPEN);
    }

    @Test
    void aNewUnitIsDueNow() {
        PollDecision decision = decide(new PollState(null, 0, null), NOW,
                match(TrackedMatchStatus.SCHEDULED, "2026-10-20T20:00"));

        assertThat(decision.nextRunAt()).isEqualTo(NOW);
    }

    @Test
    void dayBoundariesFollowTheScheduleZone() {
        // 23:30 UTC on 3 October is already 4 October in Madrid (UTC+2 in summer time).
        MatchTracking lateNight = match(TrackedMatchStatus.AWAITING_RESULT, Instant.parse("2026-10-03T23:30:00Z"));

        assertThat(decide(lateNight).level()).isEqualTo(PolicyLevel.MATCH_DAY);
    }

    @Test
    void backOffDoublesEveryThresholdUpToTheNextSlowerLevel() {
        MatchTracking open = match(TrackedMatchStatus.SCHEDULED, "2026-10-20T20:00");
        PolicyLevel level = PolicyLevel.OPEN;

        assertThat(effective(level, 0, open)).isEqualTo(Duration.ofHours(24));
        assertThat(effective(level, 2, open)).isEqualTo(Duration.ofHours(24));
        assertThat(effective(level, 3, open)).isEqualTo(Duration.ofHours(48));
        assertThat(effective(level, 5, open)).isEqualTo(Duration.ofHours(48));
        assertThat(effective(level, 6, open)).isEqualTo(Duration.ofHours(96));
        assertThat(effective(level, 9, open)).isEqualTo(Duration.ofDays(7));
        assertThat(effective(level, 300, open)).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void backOffIsCappedAtTheIntervalOfTheNextSlowerLevel() {
        MatchTracking matchDay = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T08:00");
        MatchTracking dayAfter = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-03T20:00");
        MatchTracking days = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-02T20:00");

        assertThat(effective(PolicyLevel.MATCH_DAY, 3, matchDay)).isEqualTo(Duration.ofHours(3));
        assertThat(effective(PolicyLevel.MATCH_DAY, 30, matchDay)).isEqualTo(Duration.ofHours(3));
        assertThat(effective(PolicyLevel.DAY_AFTER, 3, dayAfter)).isEqualTo(Duration.ofHours(6));
        assertThat(effective(PolicyLevel.DAY_AFTER, 6, dayAfter)).isEqualTo(Duration.ofHours(12));
        assertThat(effective(PolicyLevel.DAYS_2_TO_7, 3, days)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void aLevelChangeResetsTheCounter() {
        MatchTracking matchDay = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T08:00");

        PollDecision decision = decide(new PollState(PolicyLevel.DAY_AFTER, 7, LAST_RUN), NOW, matchDay);

        assertThat(decision.level()).isEqualTo(PolicyLevel.MATCH_DAY);
        assertThat(decision.consecutiveNoChange()).isZero();
        assertThat(decision.effectiveInterval()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void theSameLevelKeepsTheCounter() {
        MatchTracking matchDay = match(TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T08:00");

        PollDecision decision = decide(new PollState(PolicyLevel.MATCH_DAY, 4, LAST_RUN), NOW, matchDay);

        assertThat(decision.consecutiveNoChange()).isEqualTo(4);
    }

    @Test
    void fullRefreshHasAFixedIntervalAndNoBackOff() {
        PollDecision fresh = policy.fullRefresh(PollState.fresh(), settings, NOW);
        assertThat(fresh.level()).isEqualTo(PolicyLevel.FULL_REFRESH);
        assertThat(fresh.nextRunAt()).isEqualTo(NOW);

        PollDecision later = policy.fullRefresh(new PollState(PolicyLevel.FULL_REFRESH, 9, LAST_RUN), settings, NOW);
        assertThat(later.effectiveInterval()).isEqualTo(Duration.ofDays(7));
        assertThat(later.nextRunAt()).isEqualTo(LAST_RUN.plus(Duration.ofDays(7)));
    }

    @Test
    void outcomesUpdateTheCounter() {
        PollState state = new PollState(PolicyLevel.OPEN, 2, LAST_RUN);

        assertThat(policy.applyOutcome(state, RunStatus.NO_CHANGES).consecutiveNoChange()).isEqualTo(3);
        assertThat(policy.applyOutcome(state, RunStatus.SUCCEEDED).consecutiveNoChange()).isZero();
        assertThat(policy.applyOutcome(state, RunStatus.PARTIAL).consecutiveNoChange()).isZero();
        assertThat(policy.applyOutcome(state, RunStatus.FAILED).consecutiveNoChange()).isEqualTo(2);
        assertThatThrownBy(() -> policy.applyOutcome(state, RunStatus.IMPORTING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Duration effective(PolicyLevel level, int counter, MatchTracking match) {
        PollDecision decision = decide(new PollState(level, counter, LAST_RUN), NOW, match);
        assertThat(decision.level()).isEqualTo(level);
        return decision.effectiveInterval();
    }
}
