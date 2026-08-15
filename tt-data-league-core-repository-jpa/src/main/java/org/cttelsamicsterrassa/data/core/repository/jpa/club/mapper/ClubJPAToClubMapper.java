package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ClubJPAToClubMapper implements Function<ClubJPA, Club> {
    @Override
    public Club apply(ClubJPA clubJPA) {
        if (clubJPA == null) {
            return null;
        }
        ImportSource source = clubJPA.getSource() != null ? ImportSource.valueOf(clubJPA.getSource().name()) : null;
        return Club.of(clubJPA.getId(), source, clubJPA.getName());
    }
}
