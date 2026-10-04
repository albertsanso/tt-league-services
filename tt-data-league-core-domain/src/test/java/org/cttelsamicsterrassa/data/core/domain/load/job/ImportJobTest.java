package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportJobTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 4, 9, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void movesThroughStoringAndImportingToSucceeded() {
        ImportJob job = queued();
        assertTrue(job.getStatus().isActive());

        job.startStoring(NOW);
        job.startImporting();
        ImportJobSeason season = job.addSeason("2026-2027", UUID.randomUUID());
        job.recordSeasonRun(season, terminal(ImportRunStatus.SUCCESS, cleanResult()));
        job.finishFromSeasons(NOW.plusMinutes(1));

        assertEquals(ImportJobStatus.SUCCEEDED, job.getStatus());
        assertEquals(Optional.of(NOW), job.getStartedAt());
        assertEquals(Optional.of(NOW.plusMinutes(1)), job.getFinishedAt());
        assertTrue(season.getImportRunId().isPresent());
        assertTrue(job.getStatus().isTerminal());
    }

    @Test
    void rejectsIllegalTransitions() {
        ImportJob job = queued();

        assertThrows(IllegalStateException.class, job::startImporting);
        assertThrows(IllegalStateException.class, () -> job.addSeason("2026-2027", UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> job.finishFromSeasons(NOW));
        job.startStoring(NOW);
        assertThrows(IllegalStateException.class, () -> job.startStoring(NOW));
        job.fail("boom", NOW);
        assertThrows(IllegalStateException.class, () -> job.fail("again", NOW));
        assertThrows(IllegalStateException.class, job::startImporting);
    }

    @Test
    void anEmptySeasonListSucceeds() {
        ImportJob job = importing();

        job.finishFromSeasons(NOW);

        assertEquals(ImportJobStatus.SUCCEEDED, job.getStatus());
    }

    @Test
    void processorFailuresOrExecutionIssuesMakeTheJobPartial() {
        ImportJob job = importing();
        ImportJobSeason withFailures = job.addSeason("2025-2026", UUID.randomUUID());
        job.recordSeasonRun(withFailures, terminal(ImportRunStatus.SUCCESS,
                ImportProcessResult.success(List.of(), List.of(), 2, 1, 0, 1)));
        ImportJobSeason withIssues = job.addSeason("2026-2027", UUID.randomUUID());
        job.recordSeasonRun(withIssues, terminal(ImportRunStatus.SUCCESS,
                new ImportProcessResult(ImportProcessStatus.SUCCESS, List.of(), List.of(), 1, 1, 0, 0, 5, 1,
                        List.of("consolidation skipped"), List.of(), ImportLifecycleCounters.ZERO)));

        job.finishFromSeasons(NOW);

        assertEquals(ImportJobStatus.PARTIAL, job.getStatus());
        assertFalse(withFailures.isClean());
        assertFalse(withIssues.isClean());
    }

    @Test
    void aSucceededAndAFailedSeasonMakeTheJobPartial() {
        ImportJob job = importing();
        job.recordSeasonRun(job.addSeason("2025-2026", UUID.randomUUID()),
                terminal(ImportRunStatus.EMPTY_RESULT, ImportProcessResult.empty(List.of(), List.of(), 0, 0, 0)));
        job.failSeason(job.addSeason("2026-2027", UUID.randomUUID()), "busy");

        job.finishFromSeasons(NOW);

        assertEquals(ImportJobStatus.PARTIAL, job.getStatus());
    }

    @Test
    void noSucceededSeasonMakesTheJobFailed() {
        ImportJob job = importing();
        job.recordSeasonRun(job.addSeason("2026-2027", UUID.randomUUID()), terminal(ImportRunStatus.FAILURE, null));

        job.finishFromSeasons(NOW);

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertEquals(Optional.empty(), job.getErrorDetail());
    }

    @Test
    void failingTheJobFailsItsOpenSeasonsWithTheSameReason() {
        ImportJob job = importing();
        ImportJobSeason done = job.addSeason("2025-2026", UUID.randomUUID());
        job.recordSeasonRun(done, terminal(ImportRunStatus.SUCCESS, cleanResult()));
        ImportJobSeason open = job.addSeason("2026-2027", UUID.randomUUID());

        job.fail("Interrupted", NOW);

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertEquals(Optional.of("Interrupted"), job.getErrorDetail());
        assertEquals(ImportRunStatus.SUCCESS, done.getStatus());
        assertEquals(ImportRunStatus.FAILURE, open.getStatus());
        assertEquals(Optional.of("Interrupted"), open.getErrorDetail());
    }

    @Test
    void rejectsASeasonOfAnotherJobAndANonTerminalSnapshot() {
        ImportJob job = importing();
        ImportJob other = importing();
        ImportJobSeason foreign = other.addSeason("2026-2027", UUID.randomUUID());
        ImportJobSeason own = job.addSeason("2026-2027", UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> job.failSeason(foreign, "x"));
        assertThrows(IllegalArgumentException.class, () -> job.recordSeasonRun(own,
                ImportRunSnapshot.queued(UUID.randomUUID(), own.getImportResourceId(), ImportSource.FCTT,
                        "2026-2027")));
        job.failSeason(own, "x");
        assertThrows(IllegalStateException.class, () -> job.failSeason(own, "y"));
    }

    private static ImportJob queued() {
        return ImportJob.queued(UUID.randomUUID(), ImportSource.FCTT, List.of("2026-2027"), UploadMode.SNAPSHOT,
                Optional.empty(), Optional.empty(), Optional.empty(), false, Path.of("staged.zip"), "admin", NOW);
    }

    private static ImportJob importing() {
        ImportJob job = queued();
        job.startStoring(NOW);
        job.startImporting();
        return job;
    }

    private static ImportProcessResult cleanResult() {
        return ImportProcessResult.success(List.of(), List.of(), 1, 1, 0, 0);
    }

    private static ImportRunSnapshot terminal(ImportRunStatus status, ImportProcessResult result) {
        return ImportRunSnapshot.queued(UUID.randomUUID(), UUID.randomUUID(), ImportSource.FCTT, "2026-2027")
                .complete(status, ImportRunProgress.zero(), result, status == ImportRunStatus.FAILURE ? "x" : null);
    }
}
