package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchOverdueMarkTest {

    @Test
    void allComponentsAreRequired() {
        UUID id = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();

        assertThrows(NullPointerException.class, () -> new MatchOverdueMark(null, now, "admin"));
        assertThrows(NullPointerException.class, () -> new MatchOverdueMark(id, null, "admin"));
        assertThrows(IllegalArgumentException.class, () -> new MatchOverdueMark(id, now, null));
        assertThrows(IllegalArgumentException.class, () -> new MatchOverdueMark(id, now, "  "));
    }

    @Test
    void roundTripsItsComponents() {
        UUID id = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();
        MatchOverdueMark mark = new MatchOverdueMark(id, now, "admin");

        assertEquals(id, mark.matchId());
        assertEquals(now, mark.markedAt());
        assertEquals("admin", mark.markedBy());
    }
}