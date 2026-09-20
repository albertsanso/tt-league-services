package org.cttelsamicsterrassa.data.core.application.consolidation.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.consolidation.find.dto.ConsolidationActionClubReadModel;
import org.cttelsamicsterrassa.data.core.application.consolidation.find.dto.ConsolidationActionReadModel;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.consolidation.repository.ConsolidationActionRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindConsolidationActionsQueryHandler
        extends DomainQueryHandler<FindConsolidationActionsQuery, List<ConsolidationActionReadModel>> {

    private final ConsolidationActionRepository consolidationActionRepository;

    @Inject
    public FindConsolidationActionsQueryHandler(ConsolidationActionRepository consolidationActionRepository) {
        this.consolidationActionRepository = consolidationActionRepository;
    }

    @Override
    public DomainQueryResponse<List<ConsolidationActionReadModel>> handle(FindConsolidationActionsQuery query) {
        List<ConsolidationAction> actions = query.getClubId() == null
                ? consolidationActionRepository.findAllOrderByOccurredOnDesc()
                : consolidationActionRepository.findAllByClubId(query.getClubId());

        return DomainQueryResponse.sucessResponse(actions.stream().map(this::toReadModel).toList());
    }

    private ConsolidationActionReadModel toReadModel(ConsolidationAction action) {
        return new ConsolidationActionReadModel(
                action.getId(),
                action.getType(),
                action.getOccurredOn(),
                action.getPerformedByUserId(),
                action.getPerformedByUsername(),
                action.getCanonicalName(),
                toClubReadModels(action.getSourceClubs()),
                toClubReadModels(action.getTargetClubs()));
    }

    private List<ConsolidationActionClubReadModel> toClubReadModels(List<ConsolidationActionClub> clubs) {
        return clubs.stream()
                .map(club -> new ConsolidationActionClubReadModel(club.getClubId(), club.getClubName()))
                .toList();
    }
}
