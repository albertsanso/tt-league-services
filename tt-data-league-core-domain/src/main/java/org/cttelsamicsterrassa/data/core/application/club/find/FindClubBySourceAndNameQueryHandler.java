package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindClubBySourceAndNameQueryHandler extends DomainQueryHandler<FindClubBySourceAndNameQuery, Club> {

    private final ClubRepository clubRepository;

    @Inject
    public FindClubBySourceAndNameQueryHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<Club> handle(FindClubBySourceAndNameQuery query) {
        return clubRepository.findClubBySourceAndName(query.getSource(), query.getName())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
