package org.cttelsamicsterrassa.data.core.domain.load.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportLifecycleCountersTest {

    @Test
    void zeroHasAllComponentsAtZeroAndNoActivity() {
        ImportLifecycleCounters zero = ImportLifecycleCounters.ZERO;

        assertEquals(0, zero.scheduledCreated());
        assertEquals(0, zero.upgradedToPlayed());
        assertEquals(0, zero.rescheduled());
        assertEquals(0, zero.partialActas());
        assertEquals(0, zero.invalidActas());
        assertEquals(0, zero.unresolvedPendingFixtures());
        assertFalse(zero.hasActivity());
    }

    @Test
    void plusSumsEveryComponent() {
        ImportLifecycleCounters first = new ImportLifecycleCounters(1, 2, 3, 4, 5, 6);
        ImportLifecycleCounters second = new ImportLifecycleCounters(10, 20, 30, 40, 50, 60);

        assertEquals(new ImportLifecycleCounters(11, 22, 33, 44, 55, 66), first.plus(second));
        assertEquals(first, first.plus(ImportLifecycleCounters.ZERO));
    }

    @Test
    void plusRejectsANullOperand() {
        assertThrows(NullPointerException.class, () -> ImportLifecycleCounters.ZERO.plus(null));
    }

    @Test
    void negativeComponentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ImportLifecycleCounters(-1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportLifecycleCounters(0, -1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportLifecycleCounters(0, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportLifecycleCounters(0, 0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportLifecycleCounters(0, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportLifecycleCounters(0, 0, 0, 0, 0, -1));
    }

    @Test
    void hasActivityIsTrueWhenAnyComponentIsPositive() {
        assertTrue(new ImportLifecycleCounters(1, 0, 0, 0, 0, 0).hasActivity());
        assertTrue(new ImportLifecycleCounters(0, 0, 0, 0, 0, 1).hasActivity());
        assertFalse(ImportLifecycleCounters.ZERO.hasActivity());
    }
}
