package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindClubByIdQueryHandler extends DomainQueryHandler<FindClubByIdQuery, Club> {

    private final ClubRepository clubRepository;

    @Inject
    public FindClubByIdQueryHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<Club> handle(FindClubByIdQuery findClubByIdQuery) {
        return clubRepository.findClubById(findClubByIdQuery.getClubId())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
