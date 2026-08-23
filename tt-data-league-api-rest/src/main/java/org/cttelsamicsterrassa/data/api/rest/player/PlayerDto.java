package org.cttelsamicsterrassa.data.api.rest.player;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;

import java.util.UUID;

public record PlayerDto(UUID id, String name, String source, UUID canonicalPlayerId, String canonicalPlayerName) {
    public PlayerDto(UUID id, String name, String source) {
        this(id, name, source, null, null);
    }

    public static PlayerDto fromObject(FederatedPlayer player) {
        return new PlayerDto(
                player.getId(),
                player.getName(),
                player.getSource().name(),
                player.getPlayer().map(canonical -> canonical.getId()).orElse(null),
                player.getPlayer().map(canonical -> canonical.getName()).orElse(null)
        );
    }
}
