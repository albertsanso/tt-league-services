package org.cttelsamicsterrassa.data.core.application.importresource.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.ImportResourceDto;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindImportResourcesBySourceQueryHandler extends DomainQueryHandler<FindImportResourcesBySourceQuery, List<ImportResourceDto>> {

    private final ImportResourceRepository importResourceRepository;

    @Inject
    public FindImportResourcesBySourceQueryHandler(ImportResourceRepository importResourceRepository) {
        this.importResourceRepository = importResourceRepository;
    }

    @Override
    public DomainQueryResponse<List<ImportResourceDto>> handle(FindImportResourcesBySourceQuery query) {
        return DomainQueryResponse.sucessResponse(
                importResourceRepository.findBySource(query.getSource()).stream()
                .map(importResource -> {
                    return new ImportResourceDto(
                            importResource.getId(),
                            importResource.getSource().name(),
                            importResource.getSeason().toString(),
                            importResource.getType().toString(),
                            importResource.getStatus().toString(),
                            importResource.getCreated().toString(),
                            importResource.getLastProcessedDate().map(Object::toString).orElse(null));
                })
                .toList());
        }
}
