package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
