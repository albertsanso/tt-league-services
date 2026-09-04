package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewResultDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourcePreviewService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindImportPreviewStatusQueryHandler
        extends DomainQueryHandler<FindImportPreviewStatusQuery, ImportPreviewResultDto> {

    private final ImportResourceRepository importResourceRepository;
    private final ImportResourcePreviewService importResourcePreviewService;

    @Inject
    public FindImportPreviewStatusQueryHandler(ImportResourceRepository importResourceRepository,
                                               ImportResourcePreviewService importResourcePreviewService) {
        this.importResourceRepository = importResourceRepository;
        this.importResourcePreviewService = importResourcePreviewService;
    }

    @Override
    public DomainQueryResponse<ImportPreviewResultDto> handle(FindImportPreviewStatusQuery query) {
        return importResourceRepository.findById(query.getImportResourceId())
                .map(importResource -> {
                    ImportPreviewResult result = importResourcePreviewService.preview(importResource);
                    return DomainQueryResponse.sucessResponse(
                            ImportPreviewResultDtoMapper.toDto(importResource, result));
                })
                .orElseGet(() -> DomainQueryResponse.failResponse(
                        ImportPreviewResultDtoMapper.missingResource(query.getImportResourceId())));
    }
}
