package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindFederatedClubByIdQueryHandler extends DomainQueryHandler<FindFederatedClubByIdQuery, FederatedClub> {

    private final FederatedClubRepository clubRepository;

    @Inject
    public FindFederatedClubByIdQueryHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<FederatedClub> handle(FindFederatedClubByIdQuery findFederatedClubByIdQuery) {
        return clubRepository.findFederatedClubById(findFederatedClubByIdQuery.getFederatedClubId())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
