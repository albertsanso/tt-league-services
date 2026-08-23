package org.cttelsamicsterrassa.data.api.rest.player;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;

import java.util.UUID;

public record PlayerDto(UUID id, String name, String source) {
    public static PlayerDto fromObject(FederatedPlayer player) {
        return new PlayerDto(
                player.getId(),
                player.getName(),
                player.getSource().name()
        );
    }
}
