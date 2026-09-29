package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverdueGracePeriodTest {

    @Test
    void aNegativeGracePeriodIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OverdueGracePeriod(-1));
    }

    @Test
    void zeroIsAllowed() {
        assertEquals(0, new OverdueGracePeriod(0).days());
    }

    @Test
    void theDefaultIsSevenDaysForTestsAndDocumentation() {
        assertEquals(7, OverdueGracePeriod.DEFAULT.days());
    }
}