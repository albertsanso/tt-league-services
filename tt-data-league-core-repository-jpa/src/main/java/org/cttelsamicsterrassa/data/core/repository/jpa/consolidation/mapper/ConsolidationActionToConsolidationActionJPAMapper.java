package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.mapper;

import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClubRole;
import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model.ConsolidationActionClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model.ConsolidationActionJPA;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Component
public class ConsolidationActionToConsolidationActionJPAMapper
        implements Function<ConsolidationAction, ConsolidationActionJPA> {

    @Override
    public ConsolidationActionJPA apply(ConsolidationAction action) {
        if (action == null) {
            return null;
        }

        ConsolidationActionJPA actionJPA = new ConsolidationActionJPA();
        actionJPA.setId(action.getId());
        actionJPA.setType(action.getType());
        actionJPA.setOccurredOn(action.getOccurredOn());
        actionJPA.setPerformedByUserId(action.getPerformedByUserId());
        actionJPA.setPerformedByUsername(action.getPerformedByUsername());
        actionJPA.setCanonicalName(action.getCanonicalName());

        List<ConsolidationActionClubJPA> clubs = new ArrayList<>();
        clubs.addAll(toClubs(actionJPA, action.getSourceClubs(), ConsolidationActionClubRole.SOURCE));
        clubs.addAll(toClubs(actionJPA, action.getTargetClubs(), ConsolidationActionClubRole.TARGET));
        actionJPA.setClubs(clubs);

        return actionJPA;
    }

    private List<ConsolidationActionClubJPA> toClubs(
            ConsolidationActionJPA actionJPA, List<ConsolidationActionClub> clubs, ConsolidationActionClubRole role) {
        return clubs.stream()
                .map(club -> new ConsolidationActionClubJPA(
                        UUID.randomUUID(), actionJPA, role, club.getClubId(), club.getClubName()))
                .toList();
    }
}
