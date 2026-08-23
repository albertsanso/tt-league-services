package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ClubToClubJPAMapper implements Function<Club, ClubJPA> {
    @Override
    public ClubJPA apply(Club club) {
        return club == null ? null : new ClubJPA(club.getId(), club.getName());
    }
}
