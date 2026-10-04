package org.cttelsamicsterrassa.data.core.domain.resource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchCountsTest {

    @Test
    void acceptsConsistentCounts() {
        MatchCounts counts = new MatchCounts(5, 3, 2);

        assertEquals(5, counts.expected());
        assertEquals(3, counts.withResult());
        assertEquals(2, counts.pending());
        assertEquals(0, new MatchCounts(0, 0, 0).expected());
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> new MatchCounts(-1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new MatchCounts(1, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new MatchCounts(1, 2, -1));
    }

    @Test
    void rejectsCountsThatDoNotAddUp() {
        assertThrows(IllegalArgumentException.class, () -> new MatchCounts(5, 3, 1));
    }
}
