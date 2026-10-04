package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FEAT-00102: the FCTT 2026-2027 shape (TERCERA, groups 1-2, phase 1a Fase, rounds 1-3). */
class JornadaProgressCalculatorTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final OverdueGracePeriod GRACE = new OverdueGracePeriod(7);
    private static final String TERCERA = "TERCERA";
    private static final String PHASE = "1a Fase";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Test
    void computesPostponedAwaitingOverdueAndMarkedMatchesPerJornada() {
        List<MatchCalendarEntry> entries = new ArrayList<>();
        // group 2: round 1 keeps one postponed match because round 3 is already played
        entries.add(entry(2, PHASE, 1, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 12)));
        entries.add(entry(2, PHASE, 1, MatchStatus.PLAYED, LocalDate.of(2026, 9, 12)));
        entries.add(entry(2, PHASE, 2, MatchStatus.PLAYED, LocalDate.of(2026, 9, 19)));
        entries.add(entry(2, PHASE, 3, MatchStatus.PLAYED, LocalDate.of(2026, 9, 26)));
        entries.add(entry(2, PHASE, 3, MatchStatus.PLAYED, LocalDate.of(2026, 9, 26)));
        entries.add(entry(2, PHASE, 3, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 27)));
        entries.add(entry(2, PHASE, 3, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 5)));
        // group 1: nothing played yet, one match manually marked overdue
        MatchCalendarEntry marked = entry(1, PHASE, 1, MatchStatus.SCHEDULED, LocalDate.of(2026, 10, 10));
        entries.add(marked);
        entries.add(entry(1, PHASE, 1, MatchStatus.SCHEDULED, LocalDate.of(2026, 10, 11)));
        // ungrouped, phaseless group with an undated match
        entries.add(entry(null, null, 1, MatchStatus.SCHEDULED, null));

        List<RoundProgress> progress = List.of(
                progress(1, PHASE, null, null, 2, 0),
                progress(2, PHASE, 3, 2, 3, 4),
                progress(null, null, null, null, 1, 0));

        List<JornadaProgress> result = JornadaProgressCalculator.compute(progress, entries,
                Set.of(marked.matchId()), TODAY, GRACE);

        assertEquals(5, result.size());

        JornadaProgress g1r1 = result.get(0);
        assertEquals(1, g1r1.groupNumber());
        assertEquals(2, g1r1.scheduledMatches());
        assertEquals(1, g1r1.overdueMatches(), "the manual mark counts as overdue");
        assertEquals(0, g1r1.postponedMatches(), "nothing is played in group 1 yet");
        assertFalse(g1r1.current());

        JornadaProgress g2r1 = result.get(1);
        assertEquals(2, g2r1.groupNumber());
        assertEquals(1, g2r1.round());
        assertEquals(1, g2r1.scheduledMatches());
        assertEquals(1, g2r1.playedMatches());
        assertEquals(1, g2r1.postponedMatches());
        assertFalse(g2r1.complete());

        JornadaProgress g2r2 = result.get(2);
        assertTrue(g2r2.complete());
        assertEquals(0, g2r2.scheduledMatches());

        JornadaProgress g2r3 = result.get(3);
        assertTrue(g2r3.current());
        assertEquals(2, g2r3.scheduledMatches());
        assertEquals(2, g2r3.playedMatches());
        assertEquals(1, g2r3.awaitingResultMatches());
        assertEquals(1, g2r3.overdueMatches());
        assertEquals(0, g2r3.postponedMatches());
        assertEquals(LocalDate.of(2026, 9, 5), g2r3.firstDate());
        assertEquals(LocalDate.of(2026, 9, 27), g2r3.lastDate());

        JornadaProgress ungrouped = result.get(4);
        assertNull(ungrouped.groupNumber());
        assertNull(ungrouped.phase());
        assertEquals(1, ungrouped.undatedMatches());
        assertNull(ungrouped.firstDate());
        assertNull(ungrouped.lastDate());
    }

    @Test
    void aMarkOnAPlayedMatchIsIgnored() {
        MatchCalendarEntry played = entry(2, PHASE, 3, MatchStatus.PLAYED, LocalDate.of(2026, 9, 26));

        List<JornadaProgress> result = JornadaProgressCalculator.compute(
                List.of(progress(2, PHASE, 3, 3, 0, 1)), List.of(played), Set.of(played.matchId()), TODAY, GRACE);

        assertEquals(0, result.getFirst().overdueMatches());
        assertEquals(1, result.getFirst().playedMatches());
    }

    @Test
    void anEntryOfAGroupMissingFromTheProgressIsAConsistencyError() {
        MatchCalendarEntry orphan = entry(3, PHASE, 1, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 26));

        assertThrows(IllegalStateException.class, () -> JornadaProgressCalculator.compute(
                List.of(progress(2, PHASE, null, null, 1, 0)), List.of(orphan), Set.of(), TODAY, GRACE));
    }

    @Test
    void noEntriesYieldNoJornadas() {
        assertTrue(JornadaProgressCalculator.compute(List.of(), List.of(), Set.of(), TODAY, GRACE).isEmpty());
    }

    private static MatchCalendarEntry entry(Integer group, String phase, int round, MatchStatus status,
                                            LocalDate date) {
        return new MatchCalendarEntry(UUID.randomUUID(), TERCERA, group, phase, round, status, date);
    }

    private static RoundProgress progress(Integer group, String phase, Integer current, Integer lastComplete,
                                          long scheduled, long played) {
        return new RoundProgress(SOURCE, SEASON, TERCERA, group, phase, current, lastComplete, scheduled, played);
    }
}
