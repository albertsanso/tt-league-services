package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class PlayerJPAToPlayerMapper implements Function<PlayerJPA, Player> {
    @Override
    public Player apply(PlayerJPA playerJPA) {
        return playerJPA == null ? null : Player.createExisting(playerJPA.getId(), playerJPA.getName());
    }
}
