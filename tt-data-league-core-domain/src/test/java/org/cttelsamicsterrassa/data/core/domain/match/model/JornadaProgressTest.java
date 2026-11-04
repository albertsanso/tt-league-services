package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JornadaProgressTest {

    private static final OverdueGracePeriod GRACE = new OverdueGracePeriod(7);
    private static final LocalDate FIRST = LocalDate.of(2026, 9, 2);
    private static final LocalDate LAST = LocalDate.of(2026, 9, 3);

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> jornada(FIRST, LAST, -1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> jornada(FIRST, LAST, 1, -1, 0, 0, 0, 0));
    }

    @Test
    void rejectsDerivedCountsAboveScheduled() {
        assertThrows(IllegalArgumentException.class, () -> jornada(FIRST, LAST, 2, 0, 1, 1, 1, 0));
    }

    @Test
    void rejectsInconsistentDates() {
        assertThrows(IllegalArgumentException.class, () -> jornada(FIRST, null, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> jornada(null, LAST, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> jornada(LAST, FIRST, 1, 0, 0, 0, 0, 0));
    }

    @Test
    void aJornadaWithAScheduledMatchIsAlwaysOpen() {
        JornadaProgress pending = jornada(FIRST, LAST, 1, 3, 0, 0, 0, 0);

        assertTrue(pending.isOpen(LocalDate.of(2027, 1, 1), GRACE));
    }

    @Test
    void aPostponedMatchKeepsTheJornadaOpenLongAfterItsWindow() {
        JornadaProgress postponed = jornada(FIRST, LAST, 1, 3, 1, 0, 0, 0);

        assertTrue(postponed.isOpen(LocalDate.of(2026, 12, 1), GRACE));
    }

    @Test
    void aFullyPlayedJornadaStaysOpenThroughTheLastAwaitingResultDay() {
        JornadaProgress played = jornada(FIRST, LAST, 0, 4, 0, 0, 0, 0);

        assertFalse(played.isOpen(LocalDate.of(2026, 9, 1), GRACE), "before the window");
        assertTrue(played.isOpen(FIRST, GRACE));
        assertTrue(played.isOpen(LocalDate.of(2026, 9, 10), GRACE), "last date plus 7 days");
        assertFalse(played.isOpen(LocalDate.of(2026, 9, 11), GRACE), "the day after the grace window");
    }

    @Test
    void aFullyPlayedUndatedJornadaIsClosed() {
        assertFalse(jornada(null, null, 0, 2, 0, 0, 0, 0).isOpen(LocalDate.of(2026, 9, 3), GRACE));
    }

    private static JornadaProgress jornada(LocalDate first, LocalDate last, long scheduled, long played,
                                           long postponed, long overdue, long awaiting, long undated) {
        return new JornadaProgress("TERCERA", 2, "1a Fase", 1, first, last, scheduled, played,
                postponed, overdue, awaiting, undated, scheduled == 0, false);
    }
}
