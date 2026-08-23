package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ClubJPAToClubMapper implements Function<ClubJPA, Club> {
    @Override
    public Club apply(ClubJPA clubJPA) {
        return clubJPA == null ? null : Club.createExisting(clubJPA.getId(), clubJPA.getName());
    }
}
