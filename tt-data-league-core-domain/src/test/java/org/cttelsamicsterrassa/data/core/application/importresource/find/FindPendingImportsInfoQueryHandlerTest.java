package org.cttelsamicsterrassa.data.core.application.importresource.find;

import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.SourcePendingImportInfo;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.service.PendingImportsInfoFinder;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindPendingImportsInfoQueryHandlerTest {

    @Test
    void returnsLatestPendingImportForEachSource() {
        ZonedDateTime older = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime latest = older.plusDays(1);
        ImportResource olderRfetmImport = mockImportResource(ImportSource.RFETM, older);
        ImportResource latestRfetmImport = mockImportResource(ImportSource.RFETM, latest);
        ImportResource bcnEsaImport = mockImportResource(ImportSource.BCNESA, older);
        PendingImportsInfoFinder finder = mock(PendingImportsInfoFinder.class);
        when(finder.getPendingImports()).thenReturn(List.of(
                olderRfetmImport, bcnEsaImport, latestRfetmImport));

        List<SourcePendingImportInfo> result = new FindPendingImportsInfoQueryHandler(finder)
                .handle(new FindPendingImportsInfoQuery())
                .getResponse()
                .sources();

        assertEquals(List.of(
                new SourcePendingImportInfo(ImportSource.RFETM.name(), latest),
                new SourcePendingImportInfo(ImportSource.BCNESA.name(), older)), result);
    }

    private static ImportResource mockImportResource(ImportSource source, ZonedDateTime created) {
        ImportResource importResource = mock(ImportResource.class);
        when(importResource.getSource()).thenReturn(source);
        when(importResource.getCreated()).thenReturn(created);
        return importResource;
    }
}
