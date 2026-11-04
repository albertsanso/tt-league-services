package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerFixtures.NOW;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.junit.jupiter.api.Test;

class MatchDayTest {

    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void newDayIsUpcomingWithVersionZero() {
        MatchDay day = TrackerFixtures.dayStarted();

        assertThat(day.state()).isEqualTo(MatchDayState.UPCOMING);
        assertThat(day.version()).isZero();
        assertThat(day.openedAt()).isNull();
        assertThat(day.closeReason()).isNull();
        assertThat(day.lastRecomputedAt()).isEqualTo(NOW);
    }

    @Test
    void opensClosesAndReopens() {
        MatchDay open = TrackerFixtures.dayStarted().open(NOW);
        assertThat(open.state()).isEqualTo(MatchDayState.OPEN);
        assertThat(open.openedAt()).isEqualTo(NOW);

        MatchDay closed = open.close(CloseReason.MANUAL, "ana", LATER);
        assertThat(closed.state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(closed.closeReason()).isEqualTo(CloseReason.MANUAL);
        assertThat(closed.closedBy()).isEqualTo("ana");
        assertThat(closed.closedAt()).isEqualTo(LATER);

        MatchDay reopened = closed.reopen(LATER.plusSeconds(1));
        assertThat(reopened.state()).isEqualTo(MatchDayState.OPEN);
        assertThat(reopened.closeReason()).isNull();
        assertThat(reopened.closedAt()).isNull();
        assertThat(reopened.closedBy()).isNull();
        assertThat(reopened.openedAt()).isEqualTo(NOW);
    }

    @Test
    void anUpcomingDayCanBeClosed() {
        MatchDay closed = TrackerFixtures.dayStarted().close(CloseReason.REMOVED, MatchDayEvent.SYSTEM_ACTOR, NOW);

        assertThat(closed.state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(closed.openedAt()).isNull();
    }

    @Test
    void illegalTransitionsThrow() {
        MatchDay open = TrackerFixtures.openDay();
        MatchDay closed = open.close(CloseReason.MANUAL, "ana", NOW);

        assertThatThrownBy(() -> closed.close(CloseReason.MANUAL, "ana", NOW))
                .isInstanceOf(IllegalMatchDayTransitionException.class);
        assertThatThrownBy(() -> open.reopen(NOW)).isInstanceOf(IllegalMatchDayTransitionException.class);
        assertThatThrownBy(() -> open.open(NOW)).isInstanceOf(IllegalMatchDayTransitionException.class);
        assertThatThrownBy(() -> closed.open(NOW)).isInstanceOf(IllegalMatchDayTransitionException.class);
    }

    @Test
    void closedFieldsAreSetExactlyWhenClosed() {
        MatchDayKey key = TrackerFixtures.key(1);
        MatchDayWindow window = MatchDayWindow.undated(2);

        assertThatThrownBy(() -> MatchDay.restore(UUID.randomUUID(), key, window, MatchDayState.CLOSED, null, null,
                null, NOW, NOW, NOW, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchDay.restore(UUID.randomUUID(), key, window, MatchDayState.OPEN,
                CloseReason.MANUAL, NOW, "ana", NOW, NOW, NOW, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchDay.restore(UUID.randomUUID(), key, window, MatchDayState.OPEN, null, null,
                null, null, NOW, NOW, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void windowAndRecomputeTimeAreReplaced() {
        MatchDay day = TrackerFixtures.openDay();
        MatchDayWindow window = MatchDayWindow.undated(3);

        MatchDay changed = day.withWindow(window).recomputed(LATER);

        assertThat(changed.window()).isEqualTo(window);
        assertThat(changed.lastRecomputedAt()).isEqualTo(LATER);
        assertThat(changed.state()).isEqualTo(MatchDayState.OPEN);
        assertThat(day.lastRecomputedAt()).isEqualTo(NOW);
    }

    @Test
    void keyValidatesItsParts() {
        PipelineSource source = PipelineSource.FCTT;

        assertThatThrownBy(() -> new MatchDayKey(source, "2026", "TERCERA", null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayKey(source, "2026-2027", " ", null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayKey(source, "2026-2027", "TERCERA", 0, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayKey(source, "2026-2027", "TERCERA", null, " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchDayKey(source, "2026-2027", "TERCERA", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new MatchDayKey(source, "2026-2027", "TERCERA", null, null, 1).groupNumber()).isNull();
    }
}
