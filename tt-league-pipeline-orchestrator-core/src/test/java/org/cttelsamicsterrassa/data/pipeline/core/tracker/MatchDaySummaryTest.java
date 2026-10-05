package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchDaySummaryTest {

    @Test
    void activeCountsExcludeIgnoredMatchesPerStatus() {
        MatchDaySummary summary = new MatchDaySummary(TrackerFixtures.openDay(),
                Map.of(TrackedMatchStatus.REPORTED, 4, TrackedMatchStatus.OVERDUE, 2, TrackedMatchStatus.POSTPONED, 1),
                Map.of(TrackedMatchStatus.OVERDUE, 2, TrackedMatchStatus.REPORTED, 1));

        assertThat(summary.ignoredCount()).isEqualTo(3);
        assertThat(summary.reportedCount()).isEqualTo(3);
        assertThat(summary.totalCount()).isEqualTo(4);
        assertThat(summary.activeCounts()).containsEntry(TrackedMatchStatus.OVERDUE, 0)
                .containsEntry(TrackedMatchStatus.POSTPONED, 1);
        assertThat(summary.countsByStatus()).containsEntry(TrackedMatchStatus.OVERDUE, 2);
        assertThat(summary.completion()).isEqualTo(MatchDayCompletion.IN_PROGRESS);
    }

    @Test
    void anIgnoredOverdueMatchDoesNotMakeTheDayOverdue() {
        MatchDaySummary summary = new MatchDaySummary(TrackerFixtures.openDay(),
                Map.of(TrackedMatchStatus.REPORTED, 2, TrackedMatchStatus.OVERDUE, 1),
                Map.of(TrackedMatchStatus.OVERDUE, 1));

        assertThat(summary.completion()).isEqualTo(MatchDayCompletion.COMPLETE);
    }

    @Test
    void ignoredMatchesCannotExceedTheStatusCount() {
        assertThatThrownBy(() -> new MatchDaySummary(TrackerFixtures.openDay(),
                Map.of(TrackedMatchStatus.OVERDUE, 1), Map.of(TrackedMatchStatus.OVERDUE, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OVERDUE");
        assertThatThrownBy(() -> new MatchDaySummary(TrackerFixtures.openDay(),
                Map.of(TrackedMatchStatus.OVERDUE, 1), Map.of(TrackedMatchStatus.OVERDUE, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
