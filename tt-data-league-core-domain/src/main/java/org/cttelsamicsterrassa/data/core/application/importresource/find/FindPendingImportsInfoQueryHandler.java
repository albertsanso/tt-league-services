package org.cttelsamicsterrassa.data.core.application.importresource.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.PendingImportsInfoDto;
import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.SourcePendingImportInfo;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.service.PendingImportsInfoFinder;

import javax.inject.Named;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Named
public class FindPendingImportsInfoQueryHandler extends DomainQueryHandler<FindPendingImportsInfoQuery, PendingImportsInfoDto> {

    private final PendingImportsInfoFinder pendingImportsInfoFinder;

    public FindPendingImportsInfoQueryHandler(PendingImportsInfoFinder pendingImportsInfoFinder) {
        this.pendingImportsInfoFinder = pendingImportsInfoFinder;
    }

    @Override
    public DomainQueryResponse<PendingImportsInfoDto> handle(FindPendingImportsInfoQuery query) {
        PendingImportsInfoDto infos = new PendingImportsInfoDto(pendingImportsInfoFinder.getPendingImports().stream()
                .collect(Collectors.toMap(
                        importResource -> importResource.getSource().name(),
                        importResource -> importResource,
                        (first, second) -> Comparator.comparing(ImportResource::getCreated)
                                .compare(first, second) >= 0 ? first : second,
                        LinkedHashMap::new))
                .values().stream()
                .map(this::toPendingImportInfo)
                .toList());
        return DomainQueryResponse.sucessResponse(infos);
    }

    private SourcePendingImportInfo toPendingImportInfo(ImportResource importResource) {
        return new SourcePendingImportInfo(importResource.getSource().name(), importResource.getCreated());
    }
}
