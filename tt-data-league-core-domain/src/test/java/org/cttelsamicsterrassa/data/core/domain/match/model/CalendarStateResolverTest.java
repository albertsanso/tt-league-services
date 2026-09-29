package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FEAT-00092: the calendar-state rule lives only in {@link CalendarStateResolver}, so every
 * definition case is pinned here rather than in a handler or adapter.
 */
class CalendarStateResolverTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final OverdueGracePeriod GRACE = new OverdueGracePeriod(7);

    private final Team home = Team.createExisting(UUID.randomUUID(), SOURCE, "Home", SEASON, null);
    private final Team away = Team.createExisting(UUID.randomUUID(), SOURCE, "Away", SEASON, null);

    @Test
    void playedWinsOverAManualMarkALowerRoundAndAPastDate() {
        Match played = match().status(MatchStatus.PLAYED).dateTime(on(2026, 9, 1)).createExisting();

        assertEquals(CalendarMatchState.PLAYED,
                CalendarStateResolver.resolve(played, null, true, LocalDate.of(2026, 10, 1), GRACE));
        assertEquals(CalendarMatchState.PLAYED,
                CalendarStateResolver.resolve(played, 5, false, LocalDate.of(2026, 9, 1), GRACE));
    }

    @Test
    void aManualMarkWinsOverPostponedAndOverDates() {
        Match scheduled = match().round(2).dateTime(on(2026, 9, 20)).createExisting();

        assertEquals(CalendarMatchState.OVERDUE,
                CalendarStateResolver.resolve(scheduled, 3, true, LocalDate.of(2026, 9, 1), GRACE),
                "a manual mark is OVERDUE even for a future date and even over POSTPONED");
    }

    @Test
    void aRoundBelowTheCurrentRoundIsPostponedEvenWithAFutureDateAfterAReschedule() {
        Match rescheduled = match().round(1).dateTime(on(2026, 10, 30)).createExisting();

        assertEquals(CalendarMatchState.POSTPONED,
                CalendarStateResolver.resolve(rescheduled, 2, false, LocalDate.of(2026, 9, 1), GRACE));
    }

    @Test
    void aScheduledMatchWithoutADateIsUndated() {
        Match undated = match().dateTime(null).createExisting();

        assertEquals(CalendarMatchState.UNDATED,
                CalendarStateResolver.resolve(undated, null, false, LocalDate.of(2026, 9, 1), GRACE));
    }

    @Test
    void graceBoundariesWithSevenDays() {
        Match match = match().dateTime(on(2026, 9, 3)).createExisting();

        assertEquals(CalendarMatchState.AWAITING_RESULT,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 9), GRACE),
                "match date +6 days");
        assertEquals(CalendarMatchState.AWAITING_RESULT,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 10), GRACE),
                "match date +7 days, still inside the grace period");
        assertEquals(CalendarMatchState.OVERDUE,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 11), GRACE),
                "match date +8 days is past the grace period");
    }

    @Test
    void zeroGraceOverduesTheDayAfter() {
        Match match = match().dateTime(on(2026, 9, 3)).createExisting();

        assertEquals(CalendarMatchState.UPCOMING,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 3), new OverdueGracePeriod(0)));
        assertEquals(CalendarMatchState.OVERDUE,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 4), new OverdueGracePeriod(0)));
    }

    @Test
    void datedTodayAndTomorrowAreUpcoming() {
        Match match = match().dateTime(on(2026, 9, 3)).createExisting();

        assertEquals(CalendarMatchState.UPCOMING,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 3), GRACE));
        assertEquals(CalendarMatchState.UPCOMING,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 2), GRACE));
    }

    @Test
    void matchesAreDatedInCompetitionZoneNotUtc() {
        ZonedDateTime justAfterMadridMidnight = ZonedDateTime.of(
                LocalDate.of(2026, 9, 3), LocalTime.of(0, 30), Match.COMPETITION_ZONE);
        Match match = match().dateTime(justAfterMadridMidnight).createExisting();

        assertEquals(CalendarMatchState.AWAITING_RESULT,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 10), GRACE),
                "with a Madrid date of the 3rd and today the 10th the match is still inside the grace "
                        + "period; a UTC comparison would have dated it the 2nd and marked it OVERDUE");
    }

    @Test
    void nullArgumentsAreRejected() {
        Match match = match().dateTime(on(2026, 9, 3)).createExisting();
        LocalDate today = LocalDate.of(2026, 9, 1);

        assertThrows(NullPointerException.class,
                () -> CalendarStateResolver.resolve(null, null, false, today, GRACE));
        assertThrows(NullPointerException.class,
                () -> CalendarStateResolver.resolve(match, null, false, null, GRACE));
        assertThrows(NullPointerException.class,
                () -> CalendarStateResolver.resolve(match, null, false, today, null));
    }

    private ZonedDateTime on(int year, int month, int day) {
        return ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(18, 0), Match.COMPETITION_ZONE);
    }

    private Match.MatchBuilder match() {
        return Match.builder().id(UUID.randomUUID()).source(SOURCE).competition("tercera-nacional-masculino")
                .season(SEASON).groupNumber(1).round(3).phase("1a Fase")
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED);
    }
}