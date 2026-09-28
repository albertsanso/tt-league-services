package org.cttelsamicsterrassa.data.core.application.importresource.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.ImportResourceDto;
import org.cttelsamicsterrassa.data.core.application.importresource.shared.dto.RoundProgressDto;
import org.cttelsamicsterrassa.data.core.application.importresource.shared.dto.RoundProgressDtoMapper;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class FindImportResourcesBySourceQueryHandler extends DomainQueryHandler<FindImportResourcesBySourceQuery, List<ImportResourceDto>> {

    private final ImportResourceRepository importResourceRepository;
    private final MatchRepository matchRepository;

    @Inject
    public FindImportResourcesBySourceQueryHandler(ImportResourceRepository importResourceRepository,
                                                  MatchRepository matchRepository) {
        this.importResourceRepository = importResourceRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    public DomainQueryResponse<List<ImportResourceDto>> handle(FindImportResourcesBySourceQuery query) {
        // Several ACTAS resources of one season share a single progress query per handle call. The
        // rows are derived live from stored matches (FEAT-00084); nothing is persisted on the resource.
        Map<Season, List<RoundProgressDto>> progressBySeason = new HashMap<>();
        return DomainQueryResponse.sucessResponse(
                importResourceRepository.findBySource(query.getSource()).stream()
                        .map(importResource -> toDto(importResource, progressBySeason))
                        .toList());
    }

    private ImportResourceDto toDto(ImportResource importResource,
                                    Map<Season, List<RoundProgressDto>> progressBySeason) {
        return new ImportResourceDto(
                importResource.getId(),
                importResource.getSource().name(),
                importResource.getSeason().toString(),
                importResource.getType().toString(),
                importResource.getStatus().toString(),
                importResource.getCreated().toString(),
                importResource.getLastProcessedDate().map(Object::toString).orElse(null),
                roundProgress(importResource, progressBySeason));
    }

    private List<RoundProgressDto> roundProgress(ImportResource importResource,
                                                 Map<Season, List<RoundProgressDto>> progressBySeason) {
        if (importResource.getType() != ResourceType.ACTAS) {
            return List.of();
        }
        return progressBySeason.computeIfAbsent(importResource.getSeason(), season -> RoundProgressDtoMapper
                .toDtos(matchRepository.findRoundProgress(importResource.getSource(), season)));
    }
}
