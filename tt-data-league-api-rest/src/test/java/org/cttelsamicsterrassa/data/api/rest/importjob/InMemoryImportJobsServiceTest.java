package org.cttelsamicsterrassa.data.api.rest.importjob;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJobRequest;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryImportJobsServiceTest {
    private final InMemoryImportJobsService service = new InMemoryImportJobsService();

    @Test
    void lifecycleIsExplicitAndRollbackIsIdempotent() {
        ImportJob job = service.preview(new ImportJobRequest(ImportSource.RFETM, "1", true));
        assertEquals(ImportJobStatus.PREVIEW, job.status());
        job = service.validate(job.id());
        assertEquals(ImportJobStatus.READY, job.status());
        job = service.start(job.id());
        assertEquals(ImportJobStatus.SUCCEEDED, job.status());
        assertEquals(ImportJobStatus.ROLLED_BACK, service.rollback(job.id()).status());
        assertEquals(ImportJobStatus.ROLLED_BACK, service.rollback(job.id()).status());
    }

    @Test
    void rejectsUnknownMappingAndCancelsRepeatedly() {
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(new ImportJobRequest(ImportSource.FCTT, "99", true)));
        ImportJob job = service.preview(new ImportJobRequest(ImportSource.FCTT, "1", true));
        assertEquals(ImportJobStatus.CANCELLED, service.cancel(job.id()).status());
        assertEquals(ImportJobStatus.CANCELLED, service.cancel(job.id()).status());
    }

    @Test
    void rejectsSourcesOutsideConfiguredAllowList() {
        InMemoryImportJobsService restricted = new InMemoryImportJobsService(
                () -> List.of(ImportSource.RFETM));

        assertThrows(IllegalArgumentException.class,
                () -> restricted.preview(new ImportJobRequest(ImportSource.FCTT, "1", true)));
    }
}
