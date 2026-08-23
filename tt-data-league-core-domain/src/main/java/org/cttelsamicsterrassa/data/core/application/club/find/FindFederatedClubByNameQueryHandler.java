package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindFederatedClubByNameQueryHandler extends DomainQueryHandler<FindFederatedClubByNameQuery, FederatedClub> {

    private final FederatedClubRepository clubRepository;

    @Inject
    public FindFederatedClubByNameQueryHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<FederatedClub> handle(FindFederatedClubByNameQuery query) {
        return clubRepository.findFederatedClubBySourceAndName(query.getSource(), query.getFederatedClubName())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
