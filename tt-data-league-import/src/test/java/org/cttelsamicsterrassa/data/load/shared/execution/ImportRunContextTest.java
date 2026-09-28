package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00085 recording rules: the round fallback and the fixture identity conflict are warnings on
 * the reported-issues channel; they never touch the counters and never fail the run.
 */
class ImportRunContextTest {

    @Test
    void recordRoundFallbackAddsOneIssueAndLeavesCountersUnchanged() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");

        runContext.recordRoundFallback("RfetmMatchImportProcessor", Path.of("acta.json"),
                "No jornada in payload; round 3 taken from the day folder 4");

        ImportExecutionIssue issue = runContext.reportedMatchIssues().getFirst();
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertEquals("RfetmMatchImportProcessor", issue.processor());
        assertEquals("acta.json", issue.location());
        assertEquals("No jornada in payload; round 3 taken from the day folder 4", issue.message());
        assertEquals(0, runContext.matchOutcomeCounts().size());
        assertEquals(ImportLifecycleCounters.ZERO, runContext.lifecycleCounters());
    }

    @Test
    void recordRoundFallbackRejectsNullProcessorAndReason() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");

        assertThrows(NullPointerException.class,
                () -> runContext.recordRoundFallback(null, Path.of("acta.json"), "reason"));
        assertThrows(NullPointerException.class,
                () -> runContext.recordRoundFallback("Processor", Path.of("acta.json"), null));
    }

    @Test
    void fixtureIdentityConflictIsCountedAndReported() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");

        runContext.recordMatchOutcome(MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT,
                "RfetmMatchImportProcessor", Path.of("acta.json"), "duplicate fixture reason");

        assertEquals(1, runContext.matchOutcomeCounts().get(MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertEquals("duplicate fixture reason", runContext.reportedMatchIssues().getFirst().message());
        assertEquals(ImportLifecycleCounters.ZERO, runContext.lifecycleCounters());
    }

    @Test
    void warningsOnlyRunsStillSucceed() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        runContext.recordRoundFallback("RfetmMatchImportProcessor", Path.of("a.json"), "day folder round");
        runContext.recordMatchOutcome(MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT,
                "RfetmMatchImportProcessor", Path.of("b.json"), "id_partido conflict");
        runContext.recordMatchOutcome(MatchLifecycleOutcome.UNCHANGED,
                "RfetmMatchImportProcessor", Path.of("c.json"), null);

        assertEquals(ImportProcessStatus.SUCCESS, ImportRunStatusPolicy.statusOf(0, false, 3,
                runContext.lifecycleCounters()));
    }

    @Test
    void snapshotFixturesAccumulatesIdsKeysAndRoundsWithoutTouchingCounters() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();

        runContext.recordSnapshotFixture("superdivision", 1, null, 3, "ID_1", home, away);
        runContext.recordSnapshotFixture("superdivision", 1, null, 5, "ID_2", home, away);

        SnapshotFixtures fixtures = runContext.snapshotFixtures();
        assertFalse(fixtures.isEmpty());
        assertTrue(fixtures.containsFixtureId("ID_1"));
        assertTrue(fixtures.containsFixtureId("ID_2"));
        assertFalse(fixtures.containsFixtureId("ID_3"));
        assertEquals(2, fixtures.naturalKeys().size());
        assertTrue(fixtures.naturalKeys().contains(
                new SnapshotFixtures.NaturalKey("superdivision", 1, null, 3, home, away)));
        assertTrue(fixtures.naturalKeys().contains(
                new SnapshotFixtures.NaturalKey("superdivision", 1, null, 5, home, away)));
        assertEquals(OptionalInt.of(5), fixtures.highestRound("superdivision", 1, null));
        assertEquals(OptionalInt.of(5), fixtures.highestRoundOverall());
        assertEquals(0, runContext.matchOutcomeCounts().size());
        assertEquals(ImportLifecycleCounters.ZERO, runContext.lifecycleCounters());
        assertTrue(runContext.reportedMatchIssues().isEmpty());
    }

    @Test
    void snapshotFixtureWithoutTeamsRecordsIdAndRoundOnly() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        runContext.recordSnapshotFixture("tercera-nacional", 2, "1a Fase", 7, "FCTT_1", null, null);

        SnapshotFixtures fixtures = runContext.snapshotFixtures();
        assertTrue(fixtures.containsFixtureId("FCTT_1"));
        assertTrue(fixtures.naturalKeys().isEmpty());
        assertEquals(OptionalInt.of(7), fixtures.highestRound("tercera-nacional", 2, "1a Fase"));
    }

    @Test
    void snapshotFixtureNullGroupAndPhaseScopesAreDistinct() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");

        runContext.recordSnapshotFixture("lliga", null, null, 4, "A", null, null);
        runContext.recordSnapshotFixture("lliga", 1, null, 9, "B", null, null);
        runContext.recordSnapshotFixture("lliga", null, "Final", 2, "C", null, null);

        SnapshotFixtures fixtures = runContext.snapshotFixtures();
        assertEquals(OptionalInt.of(4), fixtures.highestRound("lliga", null, null));
        assertEquals(OptionalInt.of(9), fixtures.highestRound("lliga", 1, null));
        assertEquals(OptionalInt.of(2), fixtures.highestRound("lliga", null, "Final"));
        assertEquals(OptionalInt.of(9), fixtures.highestRoundOverall());
    }

    @Test
    void snapshotFixtureRejectsOneTeamIdAndNullCompetition() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        UUID team = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> runContext.recordSnapshotFixture(
                "superdivision", 1, null, 1, "ID_1", team, null));
        assertThrows(IllegalArgumentException.class, () -> runContext.recordSnapshotFixture(
                "superdivision", 1, null, 1, "ID_1", null, team));
        assertThrows(NullPointerException.class, () -> runContext.recordSnapshotFixture(
                null, 1, null, 1, "ID_1", null, null));
        assertTrue(runContext.snapshotFixtures().isEmpty());
    }
}
