package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobDto;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Reads one import job. Fails with a {@code null} body when the job does not exist.
 */
@Named
public class FindImportJobQueryHandler extends DomainQueryHandler<FindImportJobQuery, ImportJobDto> {

    private final ImportJobRepository repository;

    @Inject
    public FindImportJobQueryHandler(ImportJobRepository repository) {
        this.repository = repository;
    }

    @Override
    public DomainQueryResponse<ImportJobDto> handle(FindImportJobQuery query) {
        return repository.findById(query.getImportJobId())
                .map(job -> DomainQueryResponse.sucessResponse(ImportJobDtoMapper.toDto(job)))
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
