package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class PlayerToPlayerJPAMapper implements Function<Player, PlayerJPA> {
    @Override
    public PlayerJPA apply(Player player) {
        return player == null ? null : new PlayerJPA(player.getId(), player.getName(), player.getLicenseId());
    }
}
