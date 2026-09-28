package org.cttelsamicsterrassa.data.core.application.importresource.find;

import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.ImportResourceDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEAT-00084: the import-resource read model carries the jornada progress of each ACTAS resource,
 * derived live from stored matches and queried once per season, while everything it already exposed
 * stays exactly as it was.
 */
class FindImportResourcesBySourceQueryHandlerTest {

    private static final ZonedDateTime CREATED = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime PROCESSED = CREATED.plusDays(1);

    @Test
    void anActasResourceCarriesTheProgressOfItsOwnSourceAndSeason() {
        ImportResourceRepository resources = mock(ImportResourceRepository.class);
        MatchRepository matches = mock(MatchRepository.class);
        when(resources.findBySource("FCTT")).thenReturn(List.of(
                resource(ResourceType.ACTAS, Season.of(2026), Optional.of(PROCESSED))));
        when(matches.findRoundProgress(ImportSource.FCTT, Season.of(2026))).thenReturn(List.of(
                new RoundProgress(ImportSource.FCTT, Season.of(2026), "tercera-nacional-masculino",
                        1, "1a Fase", 1, null, 9, 3)));

        List<ImportResourceDto> rows = new FindImportResourcesBySourceQueryHandler(resources, matches)
                .handle(new FindImportResourcesBySourceQuery("FCTT"))
                .getResponse();

        assertEquals(1, rows.size());
        ImportResourceDto row = rows.getFirst();
        assertEquals("FCTT", row.source());
        assertEquals("2026-2027", row.season());
        assertEquals(PROCESSED.toString(), row.lastProcessedDate(), "the existing fields are untouched");
        assertEquals(1, row.roundProgress().size());
        assertEquals("tercera-nacional-masculino", row.roundProgress().getFirst().competition());
        assertEquals(1, row.roundProgress().getFirst().groupNumber());
        assertEquals("1a Fase", row.roundProgress().getFirst().phase());
        assertEquals(1, row.roundProgress().getFirst().currentRound());
        assertNull(row.roundProgress().getFirst().lastCompleteRound());
        assertEquals(9, row.roundProgress().getFirst().scheduledMatches());
        assertEquals(3, row.roundProgress().getFirst().playedMatches());
        verify(matches).findRoundProgress(ImportSource.FCTT, Season.of(2026));
    }

    @Test
    void severalResourcesOfOneSeasonShareOneProgressQuery() {
        ImportResourceRepository resources = mock(ImportResourceRepository.class);
        MatchRepository matches = mock(MatchRepository.class);
        when(resources.findBySource("FCTT")).thenReturn(List.of(
                resource(ResourceType.ACTAS, Season.of(2026), Optional.empty()),
                resource(ResourceType.ACTAS, Season.of(2026), Optional.of(PROCESSED))));
        when(matches.findRoundProgress(any(), any())).thenReturn(List.of());

        List<ImportResourceDto> rows = new FindImportResourcesBySourceQueryHandler(resources, matches)
                .handle(new FindImportResourcesBySourceQuery("FCTT"))
                .getResponse();

        assertEquals(2, rows.size());
        verify(matches, times(1)).findRoundProgress(ImportSource.FCTT, Season.of(2026));
    }

    @Test
    void aNonActasResourceHasNoProgressAndTriggersNoQuery() {
        ImportResourceRepository resources = mock(ImportResourceRepository.class);
        MatchRepository matches = mock(MatchRepository.class);
        when(resources.findBySource("FCTT")).thenReturn(List.of(
                resource(ResourceType.TEAMS, Season.of(2026), Optional.empty())));

        List<ImportResourceDto> rows = new FindImportResourcesBySourceQueryHandler(resources, matches)
                .handle(new FindImportResourcesBySourceQuery("FCTT"))
                .getResponse();

        assertEquals(List.of(), rows.getFirst().roundProgress());
        verify(matches, times(0)).findRoundProgress(any(), any());
    }

    private static ImportResource resource(ResourceType type, Season season, Optional<ZonedDateTime> processed) {
        Resource source = Resource.createExisting(UUID.randomUUID(), type.name(), "import/actas",
                Path.of("import", "actas"));
        return ImportResource.createExisting(UUID.randomUUID(), source, Optional.empty(), type, CREATED,
                processed, season, ImportSource.FCTT,
                org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus.PENDING);
    }
}
