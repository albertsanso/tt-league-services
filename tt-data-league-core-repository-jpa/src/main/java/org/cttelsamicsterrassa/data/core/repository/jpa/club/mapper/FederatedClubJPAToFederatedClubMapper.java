package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.FederatedClubJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class FederatedClubJPAToFederatedClubMapper implements Function<FederatedClubJPA, FederatedClub> {
    @Override
    public FederatedClub apply(FederatedClubJPA clubJPA) {
        if (clubJPA == null) {
            return null;
        }
        ImportSource source = clubJPA.getSource() != null ? ImportSource.valueOf(clubJPA.getSource().name()) : null;
        return FederatedClub.createExisting(clubJPA.getId(), source, clubJPA.getName());
    }
}
