package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.FederatedClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class FederatedClubToFederatedClubJPAMapper implements Function<FederatedClub, FederatedClubJPA> {
    @Override
    public FederatedClubJPA apply(FederatedClub club) {
        if (club == null) {
            return null;
        }
        Source source = club.getSource() != null ? Source.valueOf(club.getSource().name()) : null;
        return new FederatedClubJPA(club.getId(), source, club.getName());
    }
}
