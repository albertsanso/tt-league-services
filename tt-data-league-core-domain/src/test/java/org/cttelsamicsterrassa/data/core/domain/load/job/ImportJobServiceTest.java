package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.service.FakeImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.FakeImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceRunService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceRepositoryLoaderService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceUploadService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceZipService;
import org.cttelsamicsterrassa.data.core.domain.load.service.SnapshotShrinkException;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ManifestProvenance;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobServiceTest {

    private static final String SHA = "a".repeat(64);
    private static final byte[] ZIP = {1, 2, 3};
    private static final Duration RETRY = Duration.ofSeconds(10);
    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    @TempDir
    Path workDir;

    private final ResourceUploadService uploadService = mock(ResourceUploadService.class);
    private final ResourceZipService zipService = mock(ResourceZipService.class);
    private final ResourceRepositoryLoaderService loaderService = mock(ResourceRepositoryLoaderService.class);
    private final FakeImportRunRegistry registry = new FakeImportRunRegistry();
    private final FakeImportResourceRepository resources = new FakeImportResourceRepository();
    private final InMemoryImportJobRepository jobs = new InMemoryImportJobRepository();
    private final List<UUID> dispatched = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T09:00:00Z"));
    private final List<Duration> sleeps = new ArrayList<>();
    private final Map<String, ImportProcessResult> resultsBySeason = new HashMap<>();
    private Runnable onSleep = () -> { };
    private boolean interruptOnSleep;
    private ImportJobDispatcher dispatcher = dispatched::add;
    private Path importFolder;
    private Path extractionFolder;

    @BeforeEach
    void setUp() throws Exception {
        importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        when(zipService.getFolderFromSetting()).thenReturn(importFolder.toString());
    }

    @Test
    void submitStagesTheZipPersistsAQueuedJobAndDispatchesIt() throws Exception {
        ImportManifest manifest = manifest(List.of("2026-2027"), Optional.of(SHA), Optional.of("ingest-1"));
        when(uploadService.readManifest("upload.zip", ZIP)).thenReturn(manifest);

        SubmitResult result = service().submit("upload.zip", ZIP, Optional.of("orch-7"), true, "admin");

        ImportJob job = result.job();
        assertTrue(result.created());
        assertEquals(ImportJobStatus.QUEUED, job.getStatus());
        assertEquals(ImportSource.FCTT, job.getSource());
        assertEquals(List.of("2026-2027"), job.getSeasons());
        assertEquals(Optional.of(SHA), job.getContentSha256());
        assertEquals(Optional.of("orch-7"), job.getClientRunId());
        assertEquals(Optional.of("ingest-1"), job.getManifestRunId());
        assertTrue(job.isAllowPublishedShrink());
        assertEquals("admin", job.getRequestedBy());
        assertEquals(importFolder.resolve("import-jobs").resolve(job.getId() + ".zip"), job.getStagedZipPath());
        assertArrayEquals(ZIP, Files.readAllBytes(job.getStagedZipPath()));
        assertEquals(Optional.of(job), jobs.findById(job.getId()));
        assertEquals(List.of(job.getId()), dispatched);
        verify(loaderService).verifyPublishedActasNotShrinking(manifest, true);
        verify(zipService).deleteExtractionFolder(manifest);
    }

    @ParameterizedTest
    @EnumSource(value = ImportJobStatus.class, names = {"QUEUED", "STORING", "IMPORTING", "SUCCEEDED", "PARTIAL"})
    void submitReturnsTheExistingJobForTheSameSourceAndHash(ImportJobStatus status) {
        ImportJob existing = existingJob(ImportSource.FCTT, Optional.of(SHA), status);
        ImportManifest manifest = manifest(List.of("2026-2027"), Optional.of(SHA), Optional.empty());
        when(uploadService.readManifest("upload.zip", ZIP)).thenReturn(manifest);

        SubmitResult result = service().submit("upload.zip", ZIP, Optional.empty(), false, "admin");

        assertSame(existing, result.job());
        assertFalse(result.created());
        assertEquals(1, jobs.all().size());
        assertTrue(dispatched.isEmpty());
        verify(loaderService, never()).verifyPublishedActasNotShrinking(any(), anyBoolean());
        verify(zipService).deleteExtractionFolder(manifest);
    }

    @Test
    void submitCreatesANewJobWhenTheEarlierJobWithTheSameHashFailed() {
        existingJob(ImportSource.FCTT, Optional.of(SHA), ImportJobStatus.FAILED);
        when(uploadService.readManifest("upload.zip", ZIP))
                .thenReturn(manifest(List.of("2026-2027"), Optional.of(SHA), Optional.empty()));

        SubmitResult result = service().submit("upload.zip", ZIP, Optional.empty(), false, "admin");

        assertTrue(result.created());
        assertEquals(2, jobs.all().size());
    }

    @Test
    void submitNeverDeduplicatesWithoutAContentHashOrAcrossSources() {
        existingJob(ImportSource.FCTT, Optional.empty(), ImportJobStatus.SUCCEEDED);
        existingJob(ImportSource.RFETM, Optional.of(SHA), ImportJobStatus.SUCCEEDED);
        when(uploadService.readManifest("a.zip", ZIP))
                .thenReturn(manifest(List.of("2026-2027"), Optional.empty(), Optional.empty()));
        when(uploadService.readManifest("b.zip", ZIP))
                .thenReturn(manifest(List.of("2026-2027"), Optional.of(SHA), Optional.empty()));

        assertTrue(service().submit("a.zip", ZIP, Optional.empty(), false, "admin").created());
        assertTrue(service().submit("b.zip", ZIP, Optional.empty(), false, "admin").created());
        assertEquals(4, jobs.all().size());
    }

    @Test
    void submitPropagatesAShrinkRejectionWithoutStagingAJob() throws Exception {
        ImportManifest manifest = manifest(List.of("2026-2027"), Optional.empty(), Optional.empty());
        when(uploadService.readManifest("upload.zip", ZIP)).thenReturn(manifest);
        doThrow(new SnapshotShrinkException(UploadMode.SNAPSHOT,
                List.of(new SnapshotShrinkException.SeasonShrink("FCTT", "2026-2027", 3, 2))))
                .when(loaderService).verifyPublishedActasNotShrinking(manifest, false);

        assertThrows(SnapshotShrinkException.class,
                () -> service().submit("upload.zip", ZIP, Optional.empty(), false, "admin"));

        assertTrue(jobs.all().isEmpty());
        assertTrue(dispatched.isEmpty());
        assertFalse(Files.exists(importFolder.resolve("import-jobs")));
        verify(zipService).deleteExtractionFolder(manifest);
    }

    @Test
    void submitRejectsAnInvalidRunIdBeforeReadingTheUpload() {
        assertThrows(IllegalArgumentException.class,
                () -> service().submit("upload.zip", ZIP, Optional.of("not valid!"), false, "admin"));

        verify(uploadService, never()).readManifest(any(), any());
    }

    @Test
    void submitFailsTheJobAndDeletesTheStagedZipWhenDispatchIsRejected() {
        when(uploadService.readManifest("upload.zip", ZIP))
                .thenReturn(manifest(List.of("2026-2027"), Optional.empty(), Optional.empty()));
        dispatcher = jobId -> { throw new IllegalStateException("executor shut down"); };

        assertThrows(IllegalStateException.class,
                () -> service().submit("upload.zip", ZIP, Optional.empty(), false, "admin"));

        ImportJob job = jobs.all().get(0);
        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertEquals(Optional.of("Unable to dispatch the import job: executor shut down"), job.getErrorDetail());
        assertFalse(Files.exists(job.getStagedZipPath()));
    }

    @Test
    void executeStoresTheContentAndImportsEverySeason() throws Exception {
        ImportJob job = submitted(List.of("2025-2026", "2026-2027"), true);
        ImportManifest stagedManifest = stubStagedManifest(job);
        List<ImportResource> seasonResources = stubLoad(stagedManifest, "2025-2026", "2026-2027");

        service().execute(job.getId());

        assertEquals(ImportJobStatus.SUCCEEDED, job.getStatus());
        assertTrue(job.getFinishedAt().isPresent());
        assertEquals(2, job.getSeasonResults().size());
        for (int i = 0; i < 2; i++) {
            ImportJobSeason season = job.getSeasonResults().get(i);
            assertEquals(seasonResources.get(i).getId(), season.getImportResourceId());
            assertEquals(ImportRunStatus.SUCCESS, season.getStatus());
            assertTrue(season.getImportRunId().isPresent());
            assertTrue(season.getResult().isPresent());
            assertEquals(ImportResourceStatus.PROCESSED, seasonResources.get(i).getStatus());
        }
        assertEquals(List.of("2025-2026", "2026-2027"),
                job.getSeasonResults().stream().map(ImportJobSeason::getSeason).toList());
        verify(loaderService).verifyPublishedActasNotShrinking(stagedManifest, true);
        verify(zipService).deleteExtractionFolder(stagedManifest);
        assertFalse(Files.exists(job.getStagedZipPath()));
        assertFalse(registry.hasActiveRun());
    }

    @Test
    void executeEndsPartialWhenASeasonHasProcessorFailures() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        stubLoad(stubStagedManifest(job), "2026-2027");
        resultsBySeason.put("2026-2027", ImportProcessResult.success(List.of(), List.of(), 3, 2, 0, 1));

        service().execute(job.getId());

        assertEquals(ImportJobStatus.PARTIAL, job.getStatus());
    }

    @Test
    void executeEndsPartialWhenOneSeasonSucceedsAndAnotherFails() throws Exception {
        ImportJob job = submitted(List.of("2025-2026", "2026-2027"), false);
        stubLoad(stubStagedManifest(job), "2025-2026", "2026-2027");
        resultsBySeason.put("2026-2027", ImportProcessResult.failure(List.of(), List.of(), 3, 0, 0, 3));

        service().execute(job.getId());

        assertEquals(ImportJobStatus.PARTIAL, job.getStatus());
        assertEquals(ImportRunStatus.FAILURE, job.getSeasonResults().get(1).getStatus());
    }

    @Test
    void executeEndsFailedWhenNoSeasonSucceeds() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        stubLoad(stubStagedManifest(job), "2026-2027");
        resultsBySeason.put("2026-2027", ImportProcessResult.failure(List.of(), List.of(), 3, 0, 0, 3));

        service().execute(job.getId());

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
    }

    @Test
    void executeSucceedsWithoutSeasonsForATeamsOnlyUpload() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        stubLoad(stubStagedManifest(job));

        service().execute(job.getId());

        assertEquals(ImportJobStatus.SUCCEEDED, job.getStatus());
        assertTrue(job.getSeasonResults().isEmpty());
    }

    @Test
    void executeWaitsForAManualImportToFinishBeforeStoring() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        ImportManifest stagedManifest = stubStagedManifest(job);
        stubLoad(stagedManifest, "2026-2027");
        UUID manualRun = registry.occupy();
        onSleep = () -> {
            verify(loaderService, never()).loadIntoRepository(any());
            if (sleeps.size() == 3) {
                registry.release(manualRun);
            }
        };

        service().execute(job.getId());

        assertEquals(List.of(RETRY, RETRY, RETRY), sleeps);
        assertEquals(ImportJobStatus.SUCCEEDED, job.getStatus());
    }

    @Test
    void executeFailsWithAClearReasonWhenTheSystemStaysBusy() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        registry.occupy();

        service().execute(job.getId());

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertEquals(Optional.of("Timed out after PT1M waiting for another import to finish"),
                job.getErrorDetail());
        assertEquals(6, sleeps.size());
        verify(loaderService, never()).loadIntoRepository(any());
        assertFalse(Files.exists(job.getStagedZipPath()));
    }

    @Test
    void anInterruptWhileWaitingToStoreLeavesTheJobQueuedWithItsStagedZip() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        registry.occupy();
        interruptOnSleep = true;

        try {
            service().execute(job.getId());
            assertTrue(Thread.interrupted(), "the interrupt flag is restored");
        } finally {
            Thread.interrupted();
        }

        assertEquals(ImportJobStatus.QUEUED, job.getStatus());
        assertTrue(Files.exists(job.getStagedZipPath()));
        verify(loaderService, never()).loadIntoRepository(any());
    }

    @Test
    void aSeasonFailsWhenAnotherImportKeepsTheSystemBusyAfterStoring() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        ImportManifest stagedManifest = stubStagedManifest(job);
        ImportResource resource = resources.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.PENDING));
        when(loaderService.loadIntoRepository(stagedManifest)).thenAnswer(invocation -> {
            registry.occupy();
            return List.of(resource);
        });

        service().execute(job.getId());

        ImportJobSeason season = job.getSeasonResults().get(0);
        assertEquals(ImportRunStatus.FAILURE, season.getStatus());
        assertEquals(Optional.of("Timed out after PT1M waiting for another import to finish"),
                season.getErrorDetail());
        assertTrue(season.getImportRunId().isEmpty());
        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertEquals(ImportResourceStatus.PENDING, resource.getStatus());
    }

    @Test
    void aSeasonWhoseResourceIsAlreadyProcessingFailsAndTheOthersStillRun() throws Exception {
        ImportJob job = submitted(List.of("2025-2026", "2026-2027"), false);
        ImportManifest stagedManifest = stubStagedManifest(job);
        ImportResource processing = resources.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2025-2026", ImportResourceStatus.PROCESSING));
        ImportResource pending = resources.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.PENDING));
        when(loaderService.loadIntoRepository(stagedManifest)).thenReturn(List.of(processing, pending));

        service().execute(job.getId());

        assertEquals(ImportJobStatus.PARTIAL, job.getStatus());
        assertEquals(Optional.of("Import resource " + processing.getId() + " is already processing"),
                job.getSeasonResults().get(0).getErrorDetail());
        assertEquals(ImportRunStatus.SUCCESS, job.getSeasonResults().get(1).getStatus());
        assertEquals(ImportResourceStatus.PROCESSING, processing.getStatus());
    }

    @Test
    void executeFailsTheJobWhenStoringFails() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        ImportManifest stagedManifest = stubStagedManifest(job);
        when(loaderService.loadIntoRepository(stagedManifest))
                .thenThrow(new IllegalArgumentException("Unable to store extracted ZIP content"));

        service().execute(job.getId());

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertEquals(Optional.of("Unable to store extracted ZIP content"), job.getErrorDetail());
        verify(zipService).deleteExtractionFolder(stagedManifest);
        assertFalse(Files.exists(job.getStagedZipPath()));
    }

    @Test
    void executeFailsTheJobWhenTheStoredDataWouldNowShrink() throws Exception {
        ImportJob job = submitted(List.of("2026-2027"), false);
        ImportManifest stagedManifest = stubStagedManifest(job);
        doThrow(new SnapshotShrinkException(UploadMode.SNAPSHOT,
                List.of(new SnapshotShrinkException.SeasonShrink("FCTT", "2026-2027", 5, 2))))
                .when(loaderService).verifyPublishedActasNotShrinking(stagedManifest, false);

        service().execute(job.getId());

        assertEquals(ImportJobStatus.FAILED, job.getStatus());
        assertTrue(job.getErrorDetail().orElseThrow().contains("2026-2027"));
        verify(loaderService, never()).loadIntoRepository(any());
    }

    @Test
    void executeLeavesAJobThatIsNotQueuedUntouched() {
        ImportJob job = existingJob(ImportSource.FCTT, Optional.empty(), ImportJobStatus.SUCCEEDED);
        int saves = jobs.saveCount();

        service().execute(job.getId());

        assertEquals(ImportJobStatus.SUCCEEDED, job.getStatus());
        assertEquals(saves, jobs.saveCount());
        verify(uploadService, never()).readManifest(any(), any());
    }

    @Test
    void recoveryFailsInterruptedJobsReleasesTheirResourcesAndRedispatchesQueuedJobs() {
        ImportResource interrupted = resources.add(FakeImportResourceRepository.actasResource(
                ImportSource.FCTT, "2026-2027", ImportResourceStatus.PROCESSING));
        ImportResource manual = resources.add(FakeImportResourceRepository.actasResource(
                ImportSource.RFETM, "2026-2027", ImportResourceStatus.PROCESSING));
        ImportJob importing = existingJob(ImportSource.FCTT, Optional.empty(), ImportJobStatus.IMPORTING);
        ImportJobSeason open = importing.addSeason("2026-2027", interrupted.getId());
        ImportJob storing = existingJob(ImportSource.FCTT, Optional.empty(), ImportJobStatus.STORING);
        ImportJob firstQueued = existingJob(ImportSource.FCTT, Optional.empty(), ImportJobStatus.QUEUED);
        ImportJob secondQueued = existingJob(ImportSource.BCNESA, Optional.empty(), ImportJobStatus.QUEUED);
        ImportJob done = existingJob(ImportSource.FCTT, Optional.empty(), ImportJobStatus.SUCCEEDED);

        service().recoverAfterRestart();

        for (ImportJob job : List.of(importing, storing)) {
            assertEquals(ImportJobStatus.FAILED, job.getStatus());
            assertEquals(Optional.of("Interrupted by a platform restart"), job.getErrorDetail());
        }
        assertEquals(ImportRunStatus.FAILURE, open.getStatus());
        assertEquals(ImportResourceStatus.ERROR, interrupted.getStatus());
        assertEquals(ImportResourceStatus.PROCESSING, manual.getStatus());
        assertEquals(ImportJobStatus.SUCCEEDED, done.getStatus());
        assertEquals(List.of(firstQueued.getId(), secondQueued.getId()), dispatched);
    }

    private ImportJobService service() {
        ImportResourceProcessService processService = new ImportResourceProcessService() {
            @Override
            public ImportProcessResult process(ImportResource importResource) {
                return process(importResource, ImportProgressListener.noop());
            }

            @Override
            public ImportProcessResult process(ImportResource importResource, ImportProgressListener listener) {
                return resultsBySeason.getOrDefault(importResource.getSeason().toString(),
                        ImportProcessResult.success(List.of(), List.of(), 2, 2, 0, 0));
            }
        };
        ImportResourceRunService runService = new ImportResourceRunService(resources, processService, registry,
                clock);
        Sleeper sleeper = duration -> {
            if (interruptOnSleep) {
                throw new InterruptedException("shutdown");
            }
            sleeps.add(duration);
            clock.advance(duration);
            onSleep.run();
        };
        return new ImportJobService(uploadService, zipService, loaderService, registry, runService, resources,
                jobs, jobId -> dispatcher.dispatch(jobId), new ImportJobSettings(RETRY, TIMEOUT), clock, sleeper);
    }

    private ImportManifest manifest(List<String> seasons, Optional<String> contentSha256, Optional<String> runId) {
        ManifestProvenance provenance = new ManifestProvenance(runId, Optional.empty(), Optional.empty(),
                contentSha256, Optional.empty());
        return new ImportManifest("FCTT", seasons, Map.of("ACTAS", List.of()), extractionFolder,
                UploadMode.SNAPSHOT, provenance);
    }

    private ImportJob submitted(List<String> seasons, boolean allowShrink) {
        when(uploadService.readManifest("upload.zip", ZIP))
                .thenReturn(manifest(seasons, Optional.empty(), Optional.empty()));
        ImportJob job = service().submit("upload.zip", ZIP, Optional.empty(), allowShrink, "admin").job();
        dispatched.clear();
        return job;
    }

    private ImportManifest stubStagedManifest(ImportJob job) {
        ManifestProvenance provenance = new ManifestProvenance(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        ImportManifest stagedManifest = new ImportManifest("FCTT", job.getSeasons(), Map.of("ACTAS", List.of()),
                workDir.resolve("re-extracted"), UploadMode.SNAPSHOT, provenance);
        when(uploadService.readManifest(eq(job.getId() + ".zip"), any())).thenReturn(stagedManifest);
        return stagedManifest;
    }

    private List<ImportResource> stubLoad(ImportManifest stagedManifest, String... seasons) {
        List<ImportResource> seasonResources = new ArrayList<>();
        for (String season : seasons) {
            seasonResources.add(resources.add(FakeImportResourceRepository.actasResource(
                    ImportSource.FCTT, season, ImportResourceStatus.PENDING)));
        }
        when(loaderService.loadIntoRepository(stagedManifest)).thenReturn(seasonResources);
        return seasonResources;
    }

    private ImportJob existingJob(ImportSource source, Optional<String> contentSha256, ImportJobStatus status) {
        clock.advance(Duration.ofSeconds(1));
        ImportJob job = ImportJob.queued(UUID.randomUUID(), source, List.of("2026-2027"), UploadMode.SNAPSHOT,
                contentSha256, Optional.empty(), Optional.empty(), false,
                importFolder.resolve("import-jobs").resolve(UUID.randomUUID() + ".zip"), "admin",
                ZonedDateTime.now(clock));
        if (status != ImportJobStatus.QUEUED) {
            job.startStoring(ZonedDateTime.now(clock));
        }
        if (status == ImportJobStatus.IMPORTING || status == ImportJobStatus.SUCCEEDED
                || status == ImportJobStatus.PARTIAL) {
            job.startImporting();
        }
        if (status == ImportJobStatus.SUCCEEDED) {
            job.finishFromSeasons(ZonedDateTime.now(clock));
        } else if (status == ImportJobStatus.PARTIAL) {
            job.failSeason(job.addSeason("2025-2026", UUID.randomUUID()), "x");
            job.recordSeasonRun(job.addSeason("2026-2027", UUID.randomUUID()),
                    ImportRunSnapshot.queued(UUID.randomUUID(), UUID.randomUUID(), source, "2026-2027")
                            .complete(ImportRunStatus.SUCCESS, ImportRunProgress.zero(), null, null));
            job.finishFromSeasons(ZonedDateTime.now(clock));
        } else if (status == ImportJobStatus.FAILED) {
            job.fail("x", ZonedDateTime.now(clock));
        }
        assertEquals(status, job.getStatus());
        jobs.save(job);
        return job;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
