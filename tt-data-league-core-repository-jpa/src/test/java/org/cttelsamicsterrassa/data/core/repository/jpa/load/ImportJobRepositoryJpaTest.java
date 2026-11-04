package org.cttelsamicsterrassa.data.core.repository.jpa.load;

import jakarta.persistence.EntityManager;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSeason;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewFinding;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class ImportJobRepositoryJpaTest {

    private static final ZonedDateTime BASE = ZonedDateTime.of(2026, 10, 4, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final String SHA = "c".repeat(64);

    @Autowired
    private ImportJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void roundTripsAJobWithItsSeasonsInOrderAndTheirResults() {
        ImportJob job = job(ImportSource.FCTT, Optional.of(SHA), BASE, List.of("2025-2026", "2026-2027"));
        repository.save(job);
        job.startStoring(BASE.plusSeconds(1));
        job.startImporting();
        ImportJobSeason first = job.addSeason("2025-2026", UUID.randomUUID());
        ImportJobSeason second = job.addSeason("2026-2027", UUID.randomUUID());
        repository.save(job);
        ImportProcessResult result = richResult();
        ImportRunSnapshot run = ImportRunSnapshot.queued(UUID.randomUUID(), first.getImportResourceId(),
                ImportSource.FCTT, "2025-2026").complete(ImportRunStatus.SUCCESS, ImportRunProgress.zero(), result,
                null);
        job.recordSeasonRun(first, run);
        job.failSeason(second, "Timed out");
        job.finishFromSeasons(BASE.plusMinutes(2));
        repository.save(job);
        flushAndClear();

        ImportJob loaded = repository.findById(job.getId()).orElseThrow();

        assertEquals(ImportJobStatus.PARTIAL, loaded.getStatus());
        assertEquals(ImportSource.FCTT, loaded.getSource());
        assertEquals(List.of("2025-2026", "2026-2027"), loaded.getSeasons());
        assertEquals(UploadMode.DELTA, loaded.getMode());
        assertEquals(Optional.of(SHA), loaded.getContentSha256());
        assertEquals(Optional.of("orch-1"), loaded.getClientRunId());
        assertEquals(Optional.of("ingest-1"), loaded.getManifestRunId());
        assertTrue(loaded.isAllowPublishedShrink());
        assertEquals(Path.of("import-jobs", job.getId() + ".zip"), loaded.getStagedZipPath());
        assertEquals("admin", loaded.getRequestedBy());
        assertTrue(BASE.isEqual(loaded.getCreatedAt()));
        assertTrue(BASE.plusSeconds(1).isEqual(loaded.getStartedAt().orElseThrow()));
        assertTrue(BASE.plusMinutes(2).isEqual(loaded.getFinishedAt().orElseThrow()));

        List<ImportJobSeason> seasons = loaded.getSeasonResults();
        assertEquals(List.of("2025-2026", "2026-2027"), seasons.stream().map(ImportJobSeason::getSeason).toList());
        assertEquals(first.getId(), seasons.get(0).getId());
        assertEquals(Optional.of(run.runId()), seasons.get(0).getImportRunId());
        assertEquals(ImportRunStatus.SUCCESS, seasons.get(0).getStatus());
        assertEquals(Optional.of(result), seasons.get(0).getResult());
        assertEquals(ImportRunStatus.FAILURE, seasons.get(1).getStatus());
        assertEquals(Optional.of("Timed out"), seasons.get(1).getErrorDetail());
        assertEquals(Optional.empty(), seasons.get(1).getResult());
        assertEquals(Optional.empty(), seasons.get(1).getImportRunId());
    }

    @Test
    void findsTheMostRecentActiveOrSucceededJobOfASourceAndHash() {
        ImportJob failed = job(ImportSource.FCTT, Optional.of(SHA), BASE.plusMinutes(3), List.of("2026-2027"));
        failed.fail("x", BASE.plusMinutes(4));
        ImportJob older = job(ImportSource.FCTT, Optional.of(SHA), BASE, List.of("2026-2027"));
        ImportJob newer = job(ImportSource.FCTT, Optional.of(SHA), BASE.plusMinutes(1), List.of("2026-2027"));
        ImportJob otherSource = job(ImportSource.RFETM, Optional.of(SHA), BASE.plusMinutes(5), List.of("2026-2027"));
        List.of(failed, older, newer, otherSource).forEach(repository::save);
        flushAndClear();

        assertEquals(Optional.of(newer.getId()), repository
                .findActiveOrSucceededBySourceAndContentSha256(ImportSource.FCTT, SHA).map(ImportJob::getId));
        assertEquals(Optional.empty(), repository
                .findActiveOrSucceededBySourceAndContentSha256(ImportSource.BCNESA, SHA));
        assertEquals(Optional.empty(), repository
                .findActiveOrSucceededBySourceAndContentSha256(ImportSource.FCTT, "d".repeat(64)));
    }

    @Test
    void listsJobsNewestFirstWithOptionalFiltersAndALimit() {
        ImportJob first = job(ImportSource.FCTT, Optional.empty(), BASE, List.of("2026-2027"));
        ImportJob second = job(ImportSource.RFETM, Optional.empty(), BASE.plusDays(1), List.of("2026-2027"));
        ImportJob third = job(ImportSource.FCTT, Optional.empty(), BASE.plusDays(2), List.of("2026-2027"));
        List.of(first, second, third).forEach(repository::save);
        flushAndClear();

        assertEquals(List.of(third.getId(), second.getId(), first.getId()),
                ids(repository.find(Optional.empty(), Optional.empty(), Optional.empty(), 50)));
        assertEquals(List.of(third.getId(), first.getId()),
                ids(repository.find(Optional.of(ImportSource.FCTT), Optional.empty(), Optional.empty(), 50)));
        assertEquals(List.of(second.getId()), ids(repository.find(Optional.empty(),
                Optional.of(BASE.plusDays(1)), Optional.of(BASE.plusDays(2)), 50)));
        assertEquals(List.of(third.getId()),
                ids(repository.find(Optional.empty(), Optional.empty(), Optional.empty(), 1)));
    }

    @Test
    void findsJobsByStatusOldestFirst() {
        ImportJob newerQueued = job(ImportSource.FCTT, Optional.empty(), BASE.plusMinutes(1), List.of("2026-2027"));
        ImportJob olderQueued = job(ImportSource.FCTT, Optional.empty(), BASE, List.of("2026-2027"));
        ImportJob storing = job(ImportSource.FCTT, Optional.empty(), BASE.plusMinutes(2), List.of("2026-2027"));
        storing.startStoring(BASE.plusMinutes(3));
        List.of(newerQueued, olderQueued, storing).forEach(repository::save);
        flushAndClear();

        assertEquals(List.of(olderQueued.getId(), newerQueued.getId()),
                ids(repository.findByStatusIn(Set.of(ImportJobStatus.QUEUED))));
        assertEquals(List.of(storing.getId()),
                ids(repository.findByStatusIn(Set.of(ImportJobStatus.STORING, ImportJobStatus.IMPORTING))));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static List<UUID> ids(List<ImportJob> jobs) {
        return jobs.stream().map(ImportJob::getId).toList();
    }

    private static ImportJob job(ImportSource source, Optional<String> sha, ZonedDateTime createdAt,
                                 List<String> seasons) {
        UUID id = UUID.randomUUID();
        return ImportJob.queued(id, source, seasons, UploadMode.DELTA, sha, Optional.of("orch-1"),
                Optional.of("ingest-1"), true, Path.of("import-jobs", id + ".zip"), "admin", createdAt);
    }

    private static ImportProcessResult richResult() {
        return new ImportProcessResult(ImportProcessStatus.SUCCESS,
                List.of(new ImportPreviewFinding("WARNING", "Duplicate fixture", "a.json")),
                List.of(new ImportPreviewProcessingError("Unreadable", "b.json")),
                12, 10, 2, 1, 1500, 40, List.of("consolidation skipped"), List.of("rounds updated"),
                new ImportLifecycleCounters(3, 2, 1, 0, 1, 0, 4),
                List.of(new RoundProgress(ImportSource.FCTT, Season.fromFormatted("2025-2026"), "Lliga", 2,
                        "Primera fase", 4, null, 5, 7)));
    }
}
