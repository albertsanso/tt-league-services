package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class PlayerSeasonJPAToPlayerSeasonMapper implements Function<PlayerSeasonJPA, PlayerSeason> {

    PlayerJPAToPlayerMapper playerJPAToPlayerMapper;

    @Override
    public PlayerSeason apply(PlayerSeasonJPA playerSeasonJPA) {
        if (playerSeasonJPA == null) {
            return null;
        }
        ImportSource source = playerSeasonJPA.getSource() != null
                ? ImportSource.valueOf(playerSeasonJPA.getSource().name())
                : null;
        return PlayerSeason.of(
                playerSeasonJPA.getId(),
                source,
                playerSeasonJPA.getName(),
                playerSeasonJPA.getLicense(),
                playerJPAToPlayerMapper.apply(playerSeasonJPA.getPlayer()),
                Season.fromFormatted(playerSeasonJPA.getSeason())
        );
    }
}
