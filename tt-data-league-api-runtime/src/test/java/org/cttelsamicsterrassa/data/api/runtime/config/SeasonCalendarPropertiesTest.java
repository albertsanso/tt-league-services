package org.cttelsamicsterrassa.data.api.runtime.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FEAT-00092: the calendar grace period defaults to 7 days, binds an explicit value, and rejects a
 * negative value when the bean is materialized (no silent fallback).
 */
class SeasonCalendarPropertiesTest {

    @Test
    void defaultGracePeriodIsSevenDays() {
        assertEquals(7, new SeasonCalendarProperties().toGracePeriod().days());
    }

    @Test
    void anExplicitValueIsBound() {
        SeasonCalendarProperties properties = new SeasonCalendarProperties();
        properties.setOverdueGraceDays(3);

        assertEquals(3, properties.toGracePeriod().days());
    }

    @Test
    void aNegativeValueFailsWhenTheGracePeriodIsBuilt() {
        SeasonCalendarProperties properties = new SeasonCalendarProperties();
        properties.setOverdueGraceDays(-1);

        assertThrows(IllegalArgumentException.class, properties::toGracePeriod);
    }
}