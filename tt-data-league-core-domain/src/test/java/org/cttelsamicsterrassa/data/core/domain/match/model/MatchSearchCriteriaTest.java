package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchSearchCriteriaTest {
    @Test
    void requiresSourceSeasonAndCompetition() {
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSearchCriteria(null, Season.of(2023), "Liga", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSearchCriteria(ImportSource.RFETM, Season.of(2023), " ", null, null, null, null, null));
    }

    @Test
    void acceptsInclusiveDateBoundsAndNormalizesOptionalText() {
        var criteria = new MatchSearchCriteria(ImportSource.RFETM, Season.of(2023), " Liga ",
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 1), null, PlayerLocation.HOME, "  Ana ");
        assertEquals("Liga", criteria.competition());
        assertEquals("Ana", criteria.playerName());
        assertEquals(LocalDate.of(2023, 1, 1), criteria.fromDate());
        assertEquals(10, criteria.pageSize());
    }
}
