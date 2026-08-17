package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindClubSeasonByNameAndSeasonQueryHandler extends DomainQueryHandler<FindClubSeasonByNameAndSeasonQuery, ClubSeason> {

    private final ClubSeasonRepository clubSeasonRepository;

    @Inject
    public FindClubSeasonByNameAndSeasonQueryHandler(ClubSeasonRepository clubSeasonRepository) {
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public DomainQueryResponse<ClubSeason> handle(FindClubSeasonByNameAndSeasonQuery query) {
        return clubSeasonRepository.findClubSeasonByNameAndSeasonAndSource(query.getClubSeasonName(), query.getSeason(), null)
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
