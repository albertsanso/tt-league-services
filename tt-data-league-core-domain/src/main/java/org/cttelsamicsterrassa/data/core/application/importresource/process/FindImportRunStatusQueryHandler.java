package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportRunStatusDto;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Reads an asynchronous import run's status from the registry only. This handler must never
 * execute or restart an import; it is a pure read of whatever snapshot the registry currently holds.
 */
@Named
public class FindImportRunStatusQueryHandler
        extends DomainQueryHandler<FindImportRunStatusQuery, ImportRunStatusDto> {

    private final ImportRunRegistry runRegistry;

    @Inject
    public FindImportRunStatusQueryHandler(ImportRunRegistry runRegistry) {
        this.runRegistry = runRegistry;
    }

    @Override
    public DomainQueryResponse<ImportRunStatusDto> handle(FindImportRunStatusQuery query) {
        return runRegistry.findByRunId(query.getRunId())
                .map(snapshot -> DomainQueryResponse.sucessResponse(ImportRunStatusDtoMapper.toDto(snapshot)))
                .orElseGet(() -> DomainQueryResponse.failResponse(
                        ImportRunStatusDtoMapper.missingRun(query.getRunId())));
    }
}
