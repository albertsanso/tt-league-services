package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MatchDayWindowTest {

    private static final LocalDate FIRST = LocalDate.parse("2026-10-03");
    private static final LocalDate LAST = LocalDate.parse("2026-10-04");

    @Test
    void endIsLastDatePlusGraceDays() {
        MatchDayWindow window = new MatchDayWindow(FIRST, LAST, 2);

        assertThat(window.start()).isEqualTo(FIRST);
        assertThat(window.end()).isEqualTo(LocalDate.parse("2026-10-06"));
        assertThat(window.isDated()).isTrue();
    }

    @Test
    void graceBoundaryDayIsInsideAndTheDayAfterIsNot() {
        MatchDayWindow window = new MatchDayWindow(FIRST, LAST, 2);

        assertThat(window.contains(LocalDate.parse("2026-10-06"))).isTrue();
        assertThat(window.contains(LocalDate.parse("2026-10-07"))).isFalse();
        assertThat(window.contains(LocalDate.parse("2026-10-02"))).isFalse();
        assertThat(window.contains(FIRST)).isTrue();
    }

    @Test
    void zeroGraceEndsOnTheLastDate() {
        MatchDayWindow window = new MatchDayWindow(FIRST, LAST, 0);

        assertThat(window.end()).isEqualTo(LAST);
        assertThat(window.contains(LAST)).isTrue();
        assertThat(window.contains(LAST.plusDays(1))).isFalse();
    }

    @Test
    void hasStartedFromTheFirstDate() {
        MatchDayWindow window = new MatchDayWindow(FIRST, LAST, 2);

        assertThat(window.hasStarted(FIRST.minusDays(1))).isFalse();
        assertThat(window.hasStarted(FIRST)).isTrue();
        assertThat(window.hasStarted(LAST.plusDays(30))).isTrue();
    }

    @Test
    void undatedWindowNeverStartsAndHasNoBounds() {
        MatchDayWindow window = MatchDayWindow.undated(2);

        assertThat(window.isDated()).isFalse();
        assertThat(window.start()).isNull();
        assertThat(window.end()).isNull();
        assertThat(window.hasStarted(LAST)).isFalse();
        assertThat(window.contains(LAST)).isFalse();
    }

    @Test
    void rejectsInvalidCombinations() {
        assertThatThrownBy(() -> new MatchDayWindow(FIRST, null, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayWindow(null, LAST, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayWindow(LAST, FIRST, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayWindow(FIRST, LAST, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
