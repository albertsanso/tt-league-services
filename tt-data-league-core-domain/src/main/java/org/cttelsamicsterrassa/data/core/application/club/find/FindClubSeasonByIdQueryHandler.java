package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindClubSeasonByIdQueryHandler extends DomainQueryHandler<FindClubSeasonByIdQuery, ClubSeason> {

    private final ClubSeasonRepository clubSeasonRepository;

    @Inject
    public FindClubSeasonByIdQueryHandler(ClubSeasonRepository clubSeasonRepository) {
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public DomainQueryResponse<ClubSeason> handle(FindClubSeasonByIdQuery query) {
        return clubSeasonRepository.findClubSeasonById(query.getClubSeasonId())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
