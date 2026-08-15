package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ClubToClubJPAMapper implements Function<Club, ClubJPA> {
    @Override
    public ClubJPA apply(Club club) {
        if (club == null) {
            return null;
        }
        Source source = club.getSource() != null ? Source.valueOf(club.getSource().name()) : null;
        return new ClubJPA(club.getId(), source, club.getName());
    }
}
