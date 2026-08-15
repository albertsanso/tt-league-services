package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class PlayerToPlayerJPAMapper implements Function<Player, PlayerJPA> {
    @Override
    public PlayerJPA apply(Player player) {
        if (player == null) {
            return null;
        }
        PlayerJPA playerJPA = new PlayerJPA();
        playerJPA.setId(player.getId());
        playerJPA.setSource(player.getSource() != null ? Source.valueOf(player.getSource().name()) : null);
        playerJPA.setName(player.getName());
        return playerJPA;
    }
}
