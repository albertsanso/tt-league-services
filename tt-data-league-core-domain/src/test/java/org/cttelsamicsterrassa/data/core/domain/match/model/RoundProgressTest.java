package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FEAT-00084: {@link RoundProgress} only ever holds a progress row that an adapter could actually
 * have derived, so a contradictory row fails at construction instead of reaching a run result.
 */
class RoundProgressTest {

    private static final Season SEASON = Season.of(2026);

    @Test
    void sourceAndSeasonAreRequired() {
        NullPointerException noSource = assertThrows(NullPointerException.class,
                () -> new RoundProgress(null, null, "tercera", 1, null, null, null, 1, 0));
        assertEquals("source", noSource.getMessage());

        NullPointerException noSeason = assertThrows(NullPointerException.class,
                () -> new RoundProgress(ImportSource.FCTT, null, "tercera", 1, null, null, null, 1, 0));
        assertEquals("season", noSeason.getMessage());
    }

    @Test
    void aRowNeedsAtLeastOneStoredMatchAndNoNegativeCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null, 1, 1, -1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null, 1, null, 3, -1));

        RoundProgress pendingOnly = new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null,
                null, null, 6, 0);
        assertNull(pendingOnly.currentRound());
        assertEquals(6, pendingOnly.scheduledMatches());
    }

    @Test
    void aCompleteRoundRequiresACurrentRoundAndCannotLeadIt() {
        assertThrows(IllegalArgumentException.class,
                () -> new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null, null, 2, 0, 12));
        assertThrows(IllegalArgumentException.class,
                () -> new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null, 2, 3, 0, 18));

        assertEquals(2, new RoundProgress(ImportSource.FCTT, SEASON, "tercera", 1, null, 2, 2, 0, 12)
                .lastCompleteRound());
    }

    @Test
    void groupAndPhaseStayNullableBecauseSourcesOmitThem() {
        RoundProgress row = new RoundProgress(ImportSource.FCTT, SEASON, "tercera", null, null, 1, 1, 0, 6);

        assertNull(row.groupNumber());
        assertNull(row.phase());
    }
}
