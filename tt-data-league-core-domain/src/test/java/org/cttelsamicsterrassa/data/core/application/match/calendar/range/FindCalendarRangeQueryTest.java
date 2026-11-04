package org.cttelsamicsterrassa.data.core.application.match.calendar.range;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FindCalendarRangeQueryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

    private static FindCalendarRangeQuery query(LocalDate from, LocalDate to, String competition,
                                                Integer group) {
        return new FindCalendarRangeQuery(ImportSource.FCTT, Season.of(2026), from, to, competition, group,
                null);
    }

    @Test
    void acceptsAValidRangeWithOptionalFilters() {
        assertDoesNotThrow(() -> query(FROM, FROM.plusDays(7), null, null));
        assertDoesNotThrow(() -> new FindCalendarRangeQuery(ImportSource.FCTT, Season.of(2026), FROM,
                FROM.plusDays(1), "tercera", 2, UUID.randomUUID()));
    }

    @Test
    void requiresSourceSeasonFromAndTo() {
        assertThrows(IllegalArgumentException.class, () -> new FindCalendarRangeQuery(null, Season.of(2026),
                FROM, FROM.plusDays(1), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FindCalendarRangeQuery(ImportSource.FCTT, null,
                FROM, FROM.plusDays(1), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query(null, FROM, null, null));
        assertThrows(IllegalArgumentException.class, () -> query(FROM, null, null, null));
    }

    @Test
    void fromMustBeBeforeTo() {
        assertThrows(IllegalArgumentException.class, () -> query(FROM, FROM, null, null));
        assertThrows(IllegalArgumentException.class, () -> query(FROM, FROM.minusDays(1), null, null));
    }

    @Test
    void theRangeIsLimitedToSixtyTwoDays() {
        assertDoesNotThrow(() -> query(FROM, FROM.plusDays(FindCalendarRangeQuery.MAX_RANGE_DAYS), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> query(FROM, FROM.plusDays(FindCalendarRangeQuery.MAX_RANGE_DAYS + 1), null, null));
    }

    @Test
    void aBlankCompetitionIsRejectedAndAGroupNeedsACompetition() {
        assertThrows(IllegalArgumentException.class, () -> query(FROM, FROM.plusDays(7), "  ", null));
        assertThrows(IllegalArgumentException.class, () -> query(FROM, FROM.plusDays(7), null, 1));
        assertThrows(IllegalArgumentException.class, () -> query(FROM, FROM.plusDays(7), "tercera", 0));
    }
}
