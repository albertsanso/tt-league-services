package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00084: the jornada-progress rule lives only in {@link RoundProgressCalculator}, so every
 * definition case - including the real FCTT 2026-2027 tercera-nacional/G1 shape - is pinned here
 * rather than in an adapter or a test fixture.
 */
class RoundProgressCalculatorTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "tercera-nacional-masculino";

    @Test
    void fcttTerceraNacionalGroupOneShapeYieldsCurrentRoundOneAndNoCompleteRound() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount(TERCERA, 1, "1a Fase", 1, MatchStatus.PLAYED, 3),
                new RoundStatusCount(TERCERA, 1, "1a Fase", 1, MatchStatus.SCHEDULED, 3),
                new RoundStatusCount(TERCERA, 1, "1a Fase", 2, MatchStatus.SCHEDULED, 6)));

        assertEquals(1, progress.size());
        RoundProgress row = progress.getFirst();
        assertEquals(SOURCE, row.source());
        assertEquals(SEASON, row.season());
        assertEquals(TERCERA, row.competition());
        assertEquals(1, row.groupNumber());
        assertEquals("1a Fase", row.phase());
        assertEquals(1, row.currentRound());
        assertNull(row.lastCompleteRound(), "jornada 1 still holds pending actas");
        assertEquals(9, row.scheduledMatches());
        assertEquals(3, row.playedMatches());
    }

    @Test
    void aLegacySeasonWhoseStoredRoundsAreAllPlayedIsCompleteUpToItsHighestRound() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.PLAYED, 6),
                new RoundStatusCount(TERCERA, 1, null, 2, MatchStatus.PLAYED, 6),
                new RoundStatusCount(TERCERA, 1, null, 3, MatchStatus.PLAYED, 6)));

        RoundProgress row = progress.getFirst();
        assertEquals(3, row.currentRound());
        assertEquals(3, row.lastCompleteRound());
        assertEquals(0, row.scheduledMatches());
        assertEquals(18, row.playedMatches());
    }

    @Test
    void aMixedRoundStopsTheCompleteRoundsOneBelowIt() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.PLAYED, 6),
                new RoundStatusCount(TERCERA, 1, null, 2, MatchStatus.PLAYED, 6),
                new RoundStatusCount(TERCERA, 1, null, 3, MatchStatus.PLAYED, 4),
                new RoundStatusCount(TERCERA, 1, null, 3, MatchStatus.SCHEDULED, 2),
                new RoundStatusCount(TERCERA, 1, null, 4, MatchStatus.SCHEDULED, 6)));

        RoundProgress row = progress.getFirst();
        assertEquals(3, row.currentRound());
        assertEquals(2, row.lastCompleteRound());
        assertEquals(8, row.scheduledMatches());
        assertEquals(16, row.playedMatches());
    }

    @Test
    void aSeasonOfOnlyScheduledFixturesHasNoCurrentAndNoCompleteRound() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.SCHEDULED, 6),
                new RoundStatusCount(TERCERA, 1, null, 2, MatchStatus.SCHEDULED, 6)));

        RoundProgress row = progress.getFirst();
        assertNull(row.currentRound());
        assertNull(row.lastCompleteRound());
        assertEquals(12, row.scheduledMatches());
        assertEquals(0, row.playedMatches());
    }

    @Test
    void aPostponedEarlyFixtureKeepsTheCompleteRoundsLowWhileTheCurrentRoundAdvances() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.PLAYED, 5),
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.SCHEDULED, 1),
                new RoundStatusCount(TERCERA, 1, null, 2, MatchStatus.PLAYED, 6)));

        RoundProgress row = progress.getFirst();
        assertEquals(2, row.currentRound(), "jornada 2 already has a played match");
        assertNull(row.lastCompleteRound(), "jornada 1 is still pending, so nothing is complete");
        assertEquals(1, row.scheduledMatches());
        assertEquals(11, row.playedMatches());
    }

    @Test
    void aGapInRoundNumbersIsNotAPendingMatch() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.PLAYED, 6),
                new RoundStatusCount(TERCERA, 1, null, 3, MatchStatus.PLAYED, 6)));

        RoundProgress row = progress.getFirst();
        assertEquals(3, row.currentRound());
        assertEquals(3, row.lastCompleteRound(), "only stored rounds count; no total is inferred");
    }

    @Test
    void groupAndPhaseKeysAreIndependentAndSortLastWhenAbsent() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount("bcnesa-lliga-1", null, "2a Fase", 1, MatchStatus.PLAYED, 2),
                new RoundStatusCount("bcnesa-lliga-1", 3, "1a Fase", 1, MatchStatus.PLAYED, 2),
                new RoundStatusCount("bcnesa-lliga-1", 3, "2a Fase", 1, MatchStatus.PLAYED, 2),
                new RoundStatusCount("bcnesa-lliga-1", 3, null, 1, MatchStatus.PLAYED, 2),
                new RoundStatusCount("altres-competicio", 1, null, 1, MatchStatus.PLAYED, 2)));

        assertEquals(Arrays.asList("altres-competicio", "bcnesa-lliga-1", "bcnesa-lliga-1",
                        "bcnesa-lliga-1", "bcnesa-lliga-1"),
                progress.stream().map(RoundProgress::competition).toList());
        assertEquals(Arrays.asList(1, 3, 3, 3, null),
                progress.stream().map(RoundProgress::groupNumber).toList(),
                "the null group sorts after group 3 of the same competition");
        assertEquals(Arrays.asList(null, "1a Fase", "2a Fase", null, "2a Fase"),
                progress.stream().map(RoundProgress::phase).toList(),
                "the null phase sorts last inside its own competition and group");
    }

    @Test
    void phasesReusingRoundNumbersAreIndependentProgressRows() {
        List<RoundProgress> progress = RoundProgressCalculator.compute(SOURCE, SEASON, List.of(
                new RoundStatusCount("bcnesa-lliga-1", 3, "1a Fase", 1, MatchStatus.PLAYED, 4),
                new RoundStatusCount("bcnesa-lliga-1", 3, "1a Fase", 2, MatchStatus.PLAYED, 4),
                new RoundStatusCount("bcnesa-lliga-1", 3, "2a Fase", 1, MatchStatus.SCHEDULED, 4)));

        assertEquals(2, progress.size());
        RoundProgress firstPhase = progress.get(0);
        assertEquals("1a Fase", firstPhase.phase());
        assertEquals(2, firstPhase.currentRound());
        assertEquals(2, firstPhase.lastCompleteRound());
        RoundProgress secondPhase = progress.get(1);
        assertEquals("2a Fase", secondPhase.phase());
        assertNull(secondPhase.currentRound(), "the reused round number of the other phase is not carried over");
        assertNull(secondPhase.lastCompleteRound());
    }

    @Test
    void aSourceAndSeasonWithoutMatchesHasNoRowsAndNullCountsAreRejected() {
        assertTrue(RoundProgressCalculator.compute(SOURCE, SEASON, List.of()).isEmpty());
        assertTrue(RoundProgressCalculator.compute(SOURCE, SEASON, null).isEmpty());
    }

    @Test
    void sourceAndSeasonAreRequired() {
        List<RoundStatusCount> counts = List.of(
                new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.PLAYED, 1));
        assertThrows(NullPointerException.class, () -> RoundProgressCalculator.compute(null, SEASON, counts));
        assertThrows(NullPointerException.class, () -> RoundProgressCalculator.compute(SOURCE, null, counts));
    }

    @Test
    void oneGroupedRowAlwaysCarriesAStatusAndAtLeastOneMatch() {
        assertThrows(NullPointerException.class,
                () -> new RoundStatusCount(TERCERA, 1, null, 1, null, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RoundStatusCount(TERCERA, 1, null, 1, MatchStatus.PLAYED, 0));
    }
}
