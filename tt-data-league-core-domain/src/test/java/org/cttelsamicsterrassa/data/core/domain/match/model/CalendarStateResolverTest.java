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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void theGracePeriodStartsCountingTheDayAfterTheMatch() {
        Match match = match().dateTime(on(2026, 9, 3)).createExisting();
        OverdueGracePeriod oneDay = new OverdueGracePeriod(1);

        assertEquals(CalendarMatchState.UPCOMING,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 3), oneDay),
                "the match day itself does not count towards the grace period");
        assertEquals(CalendarMatchState.AWAITING_RESULT,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 4), oneDay),
                "the day after the match is the first grace day");
        assertEquals(CalendarMatchState.OVERDUE,
                CalendarStateResolver.resolve(match, null, false, LocalDate.of(2026, 9, 5), oneDay));
    }

    @Test
    void aScheduledMatchCanBeMarkedOverdueOnlyFromTheDayAfterItsDate() {
        Match match = match().dateTime(on(2026, 9, 3)).createExisting();

        assertFalse(CalendarStateResolver.canMarkOverdue(match, LocalDate.of(2026, 9, 2)));
        assertFalse(CalendarStateResolver.canMarkOverdue(match, LocalDate.of(2026, 9, 3)),
                "not on the match day");
        assertTrue(CalendarStateResolver.canMarkOverdue(match, LocalDate.of(2026, 9, 4)),
                "from the day after the match");
        assertTrue(CalendarStateResolver.canMarkOverdue(match, LocalDate.of(2026, 9, 20)));
    }

    @Test
    void undatedAndPlayedMatchesCannotBeMarkedOverdue() {
        Match undated = match().dateTime(null).createExisting();
        Match played = match().status(MatchStatus.PLAYED).dateTime(on(2026, 9, 1)).createExisting();

        assertFalse(CalendarStateResolver.canMarkOverdue(undated, LocalDate.of(2026, 9, 20)));
        assertFalse(CalendarStateResolver.canMarkOverdue(played, LocalDate.of(2026, 9, 20)));
    }

    @Test
    void theMarkWindowUsesTheCompetitionZoneDate() {
        ZonedDateTime lateMadridEvening = ZonedDateTime.of(
                LocalDate.of(2026, 9, 3), LocalTime.of(23, 30), Match.COMPETITION_ZONE);
        Match match = match().dateTime(lateMadridEvening).createExisting();

        assertFalse(CalendarStateResolver.canMarkOverdue(match, LocalDate.of(2026, 9, 3)));
        assertTrue(CalendarStateResolver.canMarkOverdue(match, LocalDate.of(2026, 9, 4)));
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
        assertThrows(NullPointerException.class, () -> CalendarStateResolver.canMarkOverdue(null, today));
        assertThrows(NullPointerException.class, () -> CalendarStateResolver.canMarkOverdue(match, null));
    }

    @Test
    void theProjectionOverloadAgreesWithTheMatchOverloadOnEveryBoundary() {
        for (MatchStatus status : MatchStatus.values()) {
            for (boolean marked : new boolean[] {false, true}) {
                for (Integer currentRound : new Integer[] {null, 2, 3, 4}) {
                    for (int day = 1; day <= 14; day++) {
                        Match match = match().status(status).dateTime(on(2026, 9, 3)).createExisting();
                        LocalDate today = LocalDate.of(2026, 9, day);
                        assertEquals(
                                CalendarStateResolver.resolve(match, currentRound, marked, today, GRACE),
                                CalendarStateResolver.resolve(status, 3, LocalDate.of(2026, 9, 3),
                                        currentRound, marked, today, GRACE));
                    }
                }
            }
        }
        Match undated = match().dateTime(null).createExisting();
        assertEquals(CalendarStateResolver.resolve(undated, null, false, LocalDate.of(2026, 9, 1), GRACE),
                CalendarStateResolver.resolve(MatchStatus.SCHEDULED, 3, null, null, false,
                        LocalDate.of(2026, 9, 1), GRACE));
    }

    @Test
    void theProjectionOverloadKeepsTheGraceBoundary() {
        LocalDate matchDate = LocalDate.of(2026, 9, 3);

        assertEquals(CalendarMatchState.AWAITING_RESULT, CalendarStateResolver.resolve(
                MatchStatus.SCHEDULED, 3, matchDate, null, false, LocalDate.of(2026, 9, 10), GRACE));
        assertEquals(CalendarMatchState.OVERDUE, CalendarStateResolver.resolve(
                MatchStatus.SCHEDULED, 3, matchDate, null, false, LocalDate.of(2026, 9, 11), GRACE));
        assertThrows(NullPointerException.class, () -> CalendarStateResolver.resolve(
                null, 3, matchDate, null, false, LocalDate.of(2026, 9, 1), GRACE));
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