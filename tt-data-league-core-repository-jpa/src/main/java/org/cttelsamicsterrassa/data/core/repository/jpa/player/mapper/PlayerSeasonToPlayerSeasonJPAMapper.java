package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class PlayerSeasonToPlayerSeasonJPAMapper implements Function<PlayerSeason, PlayerSeasonJPA> {

    PlayerToPlayerJPAMapper playerToPlayerJPAMapper;

    @Override
    public PlayerSeasonJPA apply(PlayerSeason playerSeason) {
        if (playerSeason == null) {
            return null;
        }
        Source source = playerSeason.getSource() != null ? Source.valueOf(playerSeason.getSource().name()) : null;
        return new PlayerSeasonJPA(
                playerSeason.getId(),
                source,
                playerSeason.getName(),
                playerSeason.getLicense(),
                playerSeason.getSeason().toString(),
                playerToPlayerJPAMapper.apply(playerSeason.getPlayer())
        );
    }
}
