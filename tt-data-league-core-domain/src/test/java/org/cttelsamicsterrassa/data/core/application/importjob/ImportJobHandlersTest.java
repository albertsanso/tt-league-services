package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobAcceptedDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobRejectionDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobSeasonDto;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobService;
import org.cttelsamicsterrassa.data.core.domain.load.job.SubmitResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.service.SnapshotShrinkException;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobHandlersTest {

    private static final ZonedDateTime CREATED = ZonedDateTime.of(2026, 10, 4, 9, 0, 0, 0, ZoneOffset.UTC);

    private final ImportJobService service = mock(ImportJobService.class);
    private final ImportJobRepository repository = mock(ImportJobRepository.class);

    @Test
    void submitReturnsTheAcceptedJob() {
        ImportJob job = queuedJob();
        when(service.submit("a.zip", new byte[]{1}, Optional.of("run-1"), true, "admin"))
                .thenReturn(new SubmitResult(job, false));

        DomainCommandResponse response = new SubmitImportJobCommandHandler(service).handle(
                new SubmitImportJobCommand("a.zip", new byte[]{1}, Optional.of("run-1"), true, "admin"));

        assertTrue(response.isSuccess());
        assertEquals(new ImportJobAcceptedDto(job.getId(), "QUEUED", false), response.getResponse());
    }

    @Test
    void submitMapsAnInvalidUploadAndAShrinkToRejections() {
        when(service.submit(eq("bad.zip"), any(), any(), anyBoolean(), any()))
                .thenThrow(new IllegalArgumentException("ZIP file must contain a root manifest.json file"));
        when(service.submit(eq("shrink.zip"), any(), any(), anyBoolean(), any()))
                .thenThrow(new SnapshotShrinkException(UploadMode.SNAPSHOT,
                        List.of(new SnapshotShrinkException.SeasonShrink("FCTT", "2026-2027", 3, 2))));
        SubmitImportJobCommandHandler handler = new SubmitImportJobCommandHandler(service);

        DomainCommandResponse invalid = handler.handle(
                new SubmitImportJobCommand("bad.zip", new byte[]{1}, Optional.empty(), false, "admin"));
        DomainCommandResponse shrink = handler.handle(
                new SubmitImportJobCommand("shrink.zip", new byte[]{1}, Optional.empty(), false, "admin"));

        assertFalse(invalid.isSuccess());
        assertEquals(new ImportJobRejectionDto(ImportJobRejectionDto.Reason.INVALID,
                "ZIP file must contain a root manifest.json file"), invalid.getResponse());
        assertFalse(shrink.isSuccess());
        assertEquals(ImportJobRejectionDto.Reason.SHRINK,
                assertInstanceOf(ImportJobRejectionDto.class, shrink.getResponse()).reason());
    }

    @Test
    void findMapsTheJobWithItsSeasonResults() {
        ImportJob job = queuedJob();
        job.startStoring(CREATED.plusSeconds(5));
        job.startImporting();
        UUID resourceId = UUID.randomUUID();
        ImportProcessResult result = new ImportProcessResult(ImportProcessStatus.SUCCESS, List.of(), List.of(),
                4, 3, 1, 0, 100, 3, List.of(), List.of(), new ImportLifecycleCounters(2, 1, 0, 0, 0, 0, 3),
                List.of(new RoundProgress(ImportSource.FCTT, Season.fromFormatted("2026-2027"), "Liga", 1, "Primera fase", 3, 2, 4, 2)));
        ImportRunSnapshot snapshot = ImportRunSnapshot.queued(UUID.randomUUID(), resourceId, ImportSource.FCTT,
                "2026-2027").complete(ImportRunStatus.SUCCESS, ImportRunProgress.zero(), result, null);
        job.recordSeasonRun(job.addSeason("2026-2027", resourceId), snapshot);
        job.finishFromSeasons(CREATED.plusMinutes(1));
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));

        DomainQueryResponse<ImportJobDto> response =
                new FindImportJobQueryHandler(repository).handle(new FindImportJobQuery(job.getId()));

        ImportJobDto dto = response.getResponse();
        assertTrue(response.isSuccess());
        assertEquals("SUCCEEDED", dto.status());
        assertEquals("FCTT", dto.source());
        assertEquals("SNAPSHOT", dto.mode());
        assertEquals(SHA, dto.contentSha256());
        assertEquals("orch-1", dto.runId());
        assertEquals("ingest-1", dto.manifestRunId());
        assertEquals(CREATED, dto.createdAt());
        assertEquals(CREATED.plusMinutes(1), dto.finishedAt());
        ImportJobSeasonDto season = dto.seasonResults().get(0);
        assertEquals(snapshot.runId(), season.importRunId());
        assertEquals("success", season.status());
        assertEquals(resourceId, season.result().importResourceId());
        assertEquals("ACTAS", season.result().resourceType());
        assertEquals(2, season.result().scheduledCreated());
        assertEquals(1, season.result().upgradedToPlayed());
        assertEquals(3, season.result().amendedPlayed());
        assertEquals(1, season.result().roundProgress().size());
    }

    @Test
    void findFailsForAnUnknownJob() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        DomainQueryResponse<ImportJobDto> response =
                new FindImportJobQueryHandler(repository).handle(new FindImportJobQuery(id));

        assertFalse(response.isSuccess());
        assertNull(response.getResponse());
    }

    @Test
    void historyTurnsInclusiveDatesIntoUtcBoundsAndDefaultsTheLimit() {
        when(repository.find(any(), any(), any(), anyInt())).thenReturn(List.of(queuedJob()));
        FindImportJobHistoryQuery query = new FindImportJobHistoryQuery(Optional.of("FCTT"),
                Optional.of(LocalDate.of(2026, 10, 1)), Optional.of(LocalDate.of(2026, 10, 4)), Optional.empty());

        DomainQueryResponse<List<ImportJobDto>> response = new FindImportJobHistoryQueryHandler(repository)
                .handle(query);

        assertEquals(1, response.getResponse().size());
        verify(repository).find(Optional.of(ImportSource.FCTT),
                Optional.of(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
                Optional.of(ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.UTC)), 50);
    }

    @Test
    void historyRejectsInvalidFilters() {
        assertThrows(IllegalArgumentException.class, () -> new FindImportJobHistoryQuery(Optional.of("ITTF"),
                Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new FindImportJobHistoryQuery(Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new FindImportJobHistoryQuery(Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(201)));
        assertThrows(IllegalArgumentException.class, () -> new FindImportJobHistoryQuery(Optional.empty(),
                Optional.of(LocalDate.of(2026, 10, 5)), Optional.of(LocalDate.of(2026, 10, 4)), Optional.empty()));
        assertEquals(200, new FindImportJobHistoryQuery(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(200)).getLimit());
    }

    private static final String SHA = "b".repeat(64);

    private static ImportJob queuedJob() {
        return ImportJob.queued(UUID.randomUUID(), ImportSource.FCTT, List.of("2026-2027"), UploadMode.SNAPSHOT,
                Optional.of(SHA), Optional.of("orch-1"), Optional.of("ingest-1"), false, Path.of("x.zip"), "admin",
                CREATED);
    }
}
