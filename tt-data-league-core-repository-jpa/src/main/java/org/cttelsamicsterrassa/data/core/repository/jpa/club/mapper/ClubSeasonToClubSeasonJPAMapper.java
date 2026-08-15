package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubSeasonJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class ClubSeasonToClubSeasonJPAMapper implements Function<ClubSeason, ClubSeasonJPA> {

    private final ClubToClubJPAMapper clubToClubJPAMapper;

    @Override
    public ClubSeasonJPA apply(ClubSeason clubSeason) {
        if (clubSeason == null) {
            return null;
        }
        ClubSeasonJPA result = new ClubSeasonJPA();
        result.setId(clubSeason.getId());
        result.setSource(clubSeason.getSource() == null ? null : Source.valueOf(clubSeason.getSource().name()));
        result.setName(clubSeason.getName());
        result.setSeason(clubSeason.getSeason().toString());
        result.setClub(clubToClubJPAMapper.apply(clubSeason.getClub()));
        return result;
    }
}
