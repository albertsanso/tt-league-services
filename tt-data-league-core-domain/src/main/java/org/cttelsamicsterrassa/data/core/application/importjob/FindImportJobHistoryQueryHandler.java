package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobDto;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Lists import jobs, most recent first, filtered by source and by inclusive UTC creation dates.
 */
@Named
public class FindImportJobHistoryQueryHandler
        extends DomainQueryHandler<FindImportJobHistoryQuery, List<ImportJobDto>> {

    private final ImportJobRepository repository;

    @Inject
    public FindImportJobHistoryQueryHandler(ImportJobRepository repository) {
        this.repository = repository;
    }

    @Override
    public DomainQueryResponse<List<ImportJobDto>> handle(FindImportJobHistoryQuery query) {
        List<ImportJobDto> jobs = repository.find(query.getSource(),
                        query.getFrom().map(date -> date.atStartOfDay(ZoneOffset.UTC)),
                        query.getTo().map(date -> date.plusDays(1).atStartOfDay(ZoneOffset.UTC)),
                        query.getLimit())
                .stream()
                .map(ImportJobDtoMapper::toDto)
                .toList();
        return DomainQueryResponse.sucessResponse(jobs);
    }
}
