package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubSeasonJPA;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class ClubSeasonJPAToClubSeasonMapper implements Function<ClubSeasonJPA, ClubSeason> {

    private final ClubJPAToClubMapper clubJPAToClubMapper;

    @Override
    public ClubSeason apply(ClubSeasonJPA clubSeasonJPA) {
        if (clubSeasonJPA == null) {
            return null;
        }
        return ClubSeason.of(
                clubSeasonJPA.getId(),
                clubSeasonJPA.getSource() == null ? null : ImportSource.valueOf(clubSeasonJPA.getSource().name()),
                clubSeasonJPA.getName(),
                Season.fromFormatted(clubSeasonJPA.getSeason()),
                clubJPAToClubMapper.apply(clubSeasonJPA.getClub())
        );
    }
}
