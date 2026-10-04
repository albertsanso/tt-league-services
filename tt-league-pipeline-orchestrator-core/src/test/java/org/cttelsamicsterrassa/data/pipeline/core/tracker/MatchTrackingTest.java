package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerFixtures.NOW;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchTrackingTest {

    private static final Instant T1 = NOW.plusSeconds(60);
    private static final Instant T2 = NOW.plusSeconds(120);
    private static final UUID RUN_1 = UUID.randomUUID();
    private static final UUID RUN_2 = UUID.randomUUID();
    private static final UUID DAY = UUID.randomUUID();

    private static MatchTracking scheduled() {
        return MatchTracking.first(UUID.randomUUID(), DAY, TrackedMatchStatus.SCHEDULED, NOW, "A", "B", NOW, null, null);
    }

    private static MatchTracking observe(MatchTracking match, TrackedMatchStatus status, Instant now, UUID run) {
        return match.observe(status, NOW, "A", "B", now, now, run);
    }

    @Test
    void reportedAtIsSetOnFirstReportAndKeptWhileReported() {
        MatchTracking reported = observe(scheduled(), TrackedMatchStatus.REPORTED, T1, RUN_1);
        assertThat(reported.reportedAt()).isEqualTo(T1);
        assertThat(reported.reportedRunId()).isEqualTo(RUN_1);

        MatchTracking later = observe(reported, TrackedMatchStatus.REPORTED, T2, RUN_2);
        assertThat(later.reportedAt()).isEqualTo(T1);
        assertThat(later.reportedRunId()).isEqualTo(RUN_1);
        assertThat(later.lastSeenAt()).isEqualTo(T2);
        assertThat(later.statusChangedAt()).isEqualTo(T1);
    }

    @Test
    void reportedAtIsClearedWhenLeavingReportedAndSetAgainOnTheNextReport() {
        MatchTracking reported = observe(scheduled(), TrackedMatchStatus.REPORTED, T1, RUN_1);

        MatchTracking reverted = observe(reported, TrackedMatchStatus.AWAITING_RESULT, T2, null);
        assertThat(reverted.reportedAt()).isNull();
        assertThat(reverted.reportedRunId()).isNull();
        assertThat(reverted.statusChangedAt()).isEqualTo(T2);

        MatchTracking again = observe(reverted, TrackedMatchStatus.REPORTED, T2.plusSeconds(1), RUN_2);
        assertThat(again.reportedAt()).isEqualTo(T2.plusSeconds(1));
        assertThat(again.reportedRunId()).isEqualTo(RUN_2);
    }

    @Test
    void periodicReportHasNoRunId() {
        MatchTracking reported = observe(scheduled(), TrackedMatchStatus.REPORTED, T1, null);

        assertThat(reported.reportedAt()).isEqualTo(T1);
        assertThat(reported.reportedRunId()).isNull();
    }

    @Test
    void firstSeenAtNeverChanges() {
        MatchTracking match = observe(scheduled(), TrackedMatchStatus.OVERDUE, T1, null);

        assertThat(match.firstSeenAt()).isEqualTo(NOW);
    }

    @Test
    void resolvedIsReportedPostponedOrIgnored() {
        assertThat(observe(scheduled(), TrackedMatchStatus.REPORTED, T1, null).isResolved()).isTrue();
        assertThat(observe(scheduled(), TrackedMatchStatus.POSTPONED, T1, null).isResolved()).isTrue();
        assertThat(observe(scheduled(), TrackedMatchStatus.OVERDUE, T1, null).isResolved()).isFalse();
        assertThat(scheduled().isResolved()).isFalse();
        assertThat(scheduled().ignore("ana", T1).isResolved()).isTrue();
    }

    @Test
    void ignoreKeepsTheDerivedStatusAndSurvivesObservations() {
        MatchTracking ignored = observe(scheduled(), TrackedMatchStatus.OVERDUE, T1, null).ignore("ana", T1);
        assertThat(ignored.isIgnored()).isTrue();
        assertThat(ignored.ignoredBy()).isEqualTo("ana");
        assertThat(ignored.status()).isEqualTo(TrackedMatchStatus.OVERDUE);

        MatchTracking observed = observe(ignored, TrackedMatchStatus.AWAITING_RESULT, T2, null);
        assertThat(observed.isIgnored()).isTrue();
        assertThat(observed.status()).isEqualTo(TrackedMatchStatus.AWAITING_RESULT);

        MatchTracking unignored = observed.unignore();
        assertThat(unignored.isIgnored()).isFalse();
        assertThat(unignored.ignoredAt()).isNull();
        assertThat(unignored.ignoredBy()).isNull();
    }

    @Test
    void ignoreAndUnignoreRejectWrongState() {
        MatchTracking ignored = scheduled().ignore("ana", T1);

        assertThatThrownBy(() -> ignored.ignore("ana", T2)).isInstanceOf(IllegalMatchDayTransitionException.class);
        assertThatThrownBy(() -> scheduled().unignore()).isInstanceOf(IllegalMatchDayTransitionException.class);
    }

    @Test
    void moveToChangesOnlyTheMatchDay() {
        UUID other = UUID.randomUUID();
        MatchTracking match = scheduled();

        MatchTracking moved = match.moveTo(other);

        assertThat(moved.matchDayId()).isEqualTo(other);
        assertThat(moved.matchId()).isEqualTo(match.matchId());
        assertThat(moved.status()).isEqualTo(match.status());
    }

    @Test
    void invariantsAreEnforced() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> MatchTracking.restore(id, DAY, TrackedMatchStatus.REPORTED, null, null, null, NOW,
                NOW, NOW, null, null, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchTracking.restore(id, DAY, TrackedMatchStatus.SCHEDULED, null, null, null, NOW,
                NOW, NOW, NOW, null, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchTracking.restore(id, DAY, TrackedMatchStatus.SCHEDULED, null, null, null, NOW,
                NOW, NOW, null, null, NOW, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchTracking.restore(id, DAY, TrackedMatchStatus.SCHEDULED, null, null, null, NOW,
                NOW, NOW, null, null, null, "ana", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
