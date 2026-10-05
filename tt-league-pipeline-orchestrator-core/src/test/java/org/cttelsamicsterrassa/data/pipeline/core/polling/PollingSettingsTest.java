package org.cttelsamicsterrassa.data.pipeline.core.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PollingSettingsTest {

    private static PollingSettings settings(
            Duration matchDay, Duration dayAfter, Duration days, Duration open, Duration overdue, Duration full) {
        return new PollingSettings(matchDay, Duration.ofHours(2), dayAfter, days, open, overdue, 21, full, 3);
    }

    @Test
    void defaultsFollowThePolicyTable() {
        PollingSettings defaults = PollingSettings.defaults();

        assertThat(defaults.interval(PolicyLevel.MATCH_DAY)).isEqualTo(Duration.ofHours(2));
        assertThat(defaults.matchDayStartOffset()).isEqualTo(Duration.ofHours(2));
        assertThat(defaults.interval(PolicyLevel.DAY_AFTER)).isEqualTo(Duration.ofHours(3));
        assertThat(defaults.interval(PolicyLevel.DAYS_2_TO_7)).isEqualTo(Duration.ofHours(12));
        assertThat(defaults.interval(PolicyLevel.OPEN)).isEqualTo(Duration.ofHours(24));
        assertThat(defaults.interval(PolicyLevel.OVERDUE)).isEqualTo(Duration.ofHours(24));
        assertThat(defaults.interval(PolicyLevel.FULL_REFRESH)).isEqualTo(Duration.ofDays(7));
        assertThat(defaults.overdueStopAfterDays()).isEqualTo(21);
        assertThat(defaults.noChangeThreshold()).isEqualTo(3);
    }

    @Test
    void stoppedHasNoInterval() {
        assertThatThrownBy(() -> PollingSettings.defaults().interval(PolicyLevel.STOPPED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDurations() {
        assertThatThrownBy(() -> settings(Duration.ZERO, Duration.ofHours(3), Duration.ofHours(12),
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofDays(7)))
                .hasMessageContaining("matchDay");
        assertThatThrownBy(() -> settings(Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(12),
                Duration.ofHours(24), Duration.ofHours(-1), Duration.ofDays(7)))
                .hasMessageContaining("overdue");
        assertThatThrownBy(() -> settings(Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(12),
                Duration.ofHours(24), Duration.ofHours(24), null))
                .hasMessageContaining("fullRefresh");
    }

    @Test
    void intervalsMustNotGetShorterForSlowerLevels() {
        assertThatThrownBy(() -> settings(Duration.ofHours(4), Duration.ofHours(3), Duration.ofHours(12),
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofDays(7)))
                .hasMessageContaining("matchDay <= dayAfter");
        assertThatThrownBy(() -> settings(Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(12),
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(12)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(12),
                Duration.ofHours(24), Duration.ofDays(8), Duration.ofDays(7)))
                .hasMessageContaining("overdue must not exceed fullRefresh");
    }

    @Test
    void stopAfterDaysAndThresholdMustBePositive() {
        Duration h = Duration.ofHours(2);
        assertThatThrownBy(() -> new PollingSettings(h, h, h, h, h, h, 0, h, 3))
                .hasMessageContaining("overdueStopAfterDays");
        assertThatThrownBy(() -> new PollingSettings(h, h, h, h, h, h, 21, h, 0))
                .hasMessageContaining("noChangeThreshold");
    }

    @Test
    void slowerLevelsFormTheBackOffChain() {
        assertThat(PolicyLevel.MATCH_DAY.slower()).isEqualTo(PolicyLevel.DAY_AFTER);
        assertThat(PolicyLevel.DAY_AFTER.slower()).isEqualTo(PolicyLevel.DAYS_2_TO_7);
        assertThat(PolicyLevel.DAYS_2_TO_7.slower()).isEqualTo(PolicyLevel.OPEN);
        assertThat(PolicyLevel.OPEN.slower()).isEqualTo(PolicyLevel.FULL_REFRESH);
        assertThat(PolicyLevel.OVERDUE.slower()).isEqualTo(PolicyLevel.FULL_REFRESH);
    }
}
