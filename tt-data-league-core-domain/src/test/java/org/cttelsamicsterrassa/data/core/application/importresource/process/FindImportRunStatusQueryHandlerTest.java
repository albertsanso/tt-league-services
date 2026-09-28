package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportProcessResultDto;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportRunStatusDto;
import org.cttelsamicsterrassa.data.core.application.importresource.shared.dto.RoundProgressDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindImportRunStatusQueryHandlerTest {

    @Test
    void returnsTheCurrentSnapshotWithoutTouchingTheImportPipeline() {
        UUID resourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ImportRunSnapshot running = ImportRunSnapshot.queued(runId, resourceId, ImportSource.RFETM, "2025-2026")
                .running(ImportRunProgress.determinate(3, 10, 1, 0));
        FindImportRunStatusQueryHandler handler = new FindImportRunStatusQueryHandler(registryWith(runId, running));

        DomainQueryResponse<ImportRunStatusDto> response = handler.handle(new FindImportRunStatusQuery(runId));

        assertTrue(response.isSuccess());
        ImportRunStatusDto dto = response.getResponse();
        assertEquals("running", dto.status());
        assertEquals(3L, dto.processed());
        assertEquals(10L, dto.total());
        assertNull(dto.result(), "result stays null until a terminal status is reached");
    }

    @Test
    void returnsTheTerminalResultOnceTheRunCompletes() {
        UUID resourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ImportProcessResult result = ImportProcessResult.success(List.of(), List.of(), 4, 4, 0, 0);
        ImportRunSnapshot completed = ImportRunSnapshot.queued(runId, resourceId, ImportSource.RFETM, "2025-2026")
                .running(ImportRunProgress.zero())
                .complete(ImportRunStatus.SUCCESS, ImportRunProgress.determinate(4, 4, 0, 0), result, null);
        FindImportRunStatusQueryHandler handler = new FindImportRunStatusQueryHandler(registryWith(runId, completed));

        DomainQueryResponse<ImportRunStatusDto> response = handler.handle(new FindImportRunStatusQuery(runId));

        ImportRunStatusDto dto = response.getResponse();
        assertEquals("success", dto.status());
        assertEquals("success", dto.result().status());
        assertEquals(4, dto.result().itemsPersisted());
    }

    @Test
    void terminalResultDtoCarriesTheLifecycleCounters() {
        UUID resourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ImportProcessResult result = new ImportProcessResult(ImportProcessStatus.SUCCESS, List.of(), List.of(),
                4, 4, 0, 0, 10, 4, List.of(), List.of(),
                new ImportLifecycleCounters(2, 1, 3, 4, 5, 6));
        ImportRunSnapshot completed = ImportRunSnapshot.queued(runId, resourceId, ImportSource.RFETM, "2025-2026")
                .running(ImportRunProgress.zero())
                .complete(ImportRunStatus.SUCCESS, ImportRunProgress.determinate(4, 4, 0, 0), result, null);
        FindImportRunStatusQueryHandler handler = new FindImportRunStatusQueryHandler(registryWith(runId, completed));

        DomainQueryResponse<ImportRunStatusDto> response = handler.handle(new FindImportRunStatusQuery(runId));

        ImportProcessResultDto dto = response.getResponse().result();
        assertEquals(2, dto.scheduledCreated());
        assertEquals(1, dto.upgradedToPlayed());
        assertEquals(3, dto.rescheduled());
        assertEquals(4, dto.partialActas());
        assertEquals(5, dto.invalidActas());
        assertEquals(6, dto.unresolvedPendingFixtures());
    }

    @Test
    void terminalResultDtoCarriesTheJornadaProgressRows() {
        UUID resourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ImportProcessResult result = new ImportProcessResult(ImportProcessStatus.SUCCESS, List.of(), List.of(),
                12, 12, 0, 0, 10, 12, List.of(), List.of(), ImportLifecycleCounters.ZERO,
                List.of(new RoundProgress(ImportSource.FCTT, Season.of(2026), "tercera-nacional-masculino",
                        1, "1a Fase", 1, null, 9, 3)));
        ImportRunSnapshot completed = ImportRunSnapshot.queued(runId, resourceId, ImportSource.FCTT, "2026-2027")
                .running(ImportRunProgress.zero())
                .complete(ImportRunStatus.SUCCESS, ImportRunProgress.determinate(12, 12, 0, 0), result, null);
        FindImportRunStatusQueryHandler handler = new FindImportRunStatusQueryHandler(registryWith(runId, completed));

        DomainQueryResponse<ImportRunStatusDto> response = handler.handle(new FindImportRunStatusQuery(runId));

        List<RoundProgressDto> progress = response.getResponse().result().roundProgress();
        assertEquals(1, progress.size());
        RoundProgressDto row = progress.getFirst();
        assertEquals("tercera-nacional-masculino", row.competition());
        assertEquals(1, row.groupNumber());
        assertEquals("1a Fase", row.phase());
        assertEquals(1, row.currentRound());
        assertNull(row.lastCompleteRound());
        assertEquals(9, row.scheduledMatches());
        assertEquals(3, row.playedMatches());
    }

    @Test
    void aResultWithoutProgressExposesAnEmptyList() {
        UUID resourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ImportRunSnapshot completed = ImportRunSnapshot.queued(runId, resourceId, ImportSource.RFETM, "2025-2026")
                .running(ImportRunProgress.zero())
                .complete(ImportRunStatus.SUCCESS, ImportRunProgress.determinate(4, 4, 0, 0),
                        ImportProcessResult.success(List.of(), List.of(), 4, 4, 0, 0), null);
        FindImportRunStatusQueryHandler handler = new FindImportRunStatusQueryHandler(registryWith(runId, completed));

        DomainQueryResponse<ImportRunStatusDto> response = handler.handle(new FindImportRunStatusQuery(runId));

        assertEquals(List.of(), response.getResponse().result().roundProgress());
    }

    @Test
    void returnsFailureForAnUnknownRun() {
        FindImportRunStatusQueryHandler handler = new FindImportRunStatusQueryHandler(registryWith(null, null));

        DomainQueryResponse<ImportRunStatusDto> response = handler.handle(new FindImportRunStatusQuery(UUID.randomUUID()));

        assertFalse(response.isSuccess());
    }

    private static ImportRunRegistry registryWith(UUID runId, ImportRunSnapshot snapshot) {
        Map<UUID, ImportRunSnapshot> runs = new HashMap<>();
        if (runId != null) {
            runs.put(runId, snapshot);
        }
        return new ImportRunRegistry() {
            @Override
            public Optional<ImportRunSnapshot> registerQueued(UUID importResourceId, ImportSource source, String season) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ImportRunSnapshot> markRunning(UUID id, ImportRunProgress progress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ImportRunSnapshot> updateProgress(UUID id, ImportRunProgress progress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ImportRunSnapshot> complete(UUID id, ImportRunStatus terminalStatus,
                                                        ImportRunProgress progress, ImportProcessResult result,
                                                        String errorDetail) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ImportRunSnapshot> findByRunId(UUID id) {
                return Optional.ofNullable(runs.get(id));
            }

            @Override
            public Optional<ImportRunSnapshot> findActiveByImportResourceId(UUID importResourceId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
