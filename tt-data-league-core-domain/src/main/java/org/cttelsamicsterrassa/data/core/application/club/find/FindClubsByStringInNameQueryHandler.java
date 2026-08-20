package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindClubsByStringInNameQueryHandler extends DomainQueryHandler<FindClubsByStringInNameQuery, List<Club>> {

    private final ClubRepository clubRepository;

    @Inject
    public FindClubsByStringInNameQueryHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<List<Club>> handle(FindClubsByStringInNameQuery findClubsByStringInNameQuery) {

        return DomainQueryResponse.sucessResponse(
            clubRepository.findAllClubsByFragmentsInName(
                    List.of(findClubsByStringInNameQuery.getStringToSearch().split(" ")))
                .stream().toList()
        );

    }
}
