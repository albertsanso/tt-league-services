package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.mapper;

import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClubRole;
import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model.ConsolidationActionClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model.ConsolidationActionJPA;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class ConsolidationActionJPAToConsolidationActionMapper
        implements Function<ConsolidationActionJPA, ConsolidationAction> {

    @Override
    public ConsolidationAction apply(ConsolidationActionJPA actionJPA) {
        if (actionJPA == null) {
            return null;
        }

        return ConsolidationAction.createExisting(
                actionJPA.getId(),
                actionJPA.getType(),
                actionJPA.getOccurredOn(),
                actionJPA.getPerformedByUserId(),
                actionJPA.getPerformedByUsername(),
                actionJPA.getCanonicalName(),
                clubsWithRole(actionJPA, ConsolidationActionClubRole.SOURCE),
                clubsWithRole(actionJPA, ConsolidationActionClubRole.TARGET));
    }

    private List<ConsolidationActionClub> clubsWithRole(
            ConsolidationActionJPA actionJPA, ConsolidationActionClubRole role) {
        List<ConsolidationActionClubJPA> clubs = actionJPA.getClubs();
        if (clubs == null) {
            return List.of();
        }
        return clubs.stream()
                .filter(club -> club.getRole() == role)
                .map(club -> ConsolidationActionClub.of(club.getClubId(), club.getClubName()))
                .toList();
    }
}
