package org.cttelsamicsterrassa.data.core.domain.shared.port;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJobRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobsPort {
    ImportJob preview(ImportJobRequest request);
    ImportJob validate(UUID id);
    ImportJob start(UUID id);
    ImportJob cancel(UUID id);
    ImportJob rollback(UUID id);
    Optional<ImportJob> find(UUID id);
    List<ImportJob> history(String query, int limit);
}
