package org.cttelsamicsterrassa.data.api.rest.player;

import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;

import java.util.List;
import java.util.UUID;

public record PlayerDto(
        UUID id,
        String name,
        String source,
        UUID canonicalPlayerId,
        String canonicalPlayerName,
        List<FederatedDto> federatedPlayers,
        List<String> sources) {
    public PlayerDto(UUID id, String name, String source) {
        this(id, name, source, null, null, List.of(), source == null ? List.of() : List.of(source));
    }

    public PlayerDto(UUID id, String name, String source, UUID canonicalPlayerId, String canonicalPlayerName) {
        this(id, name, source, canonicalPlayerId, canonicalPlayerName, List.of(),
                source == null ? List.of() : List.of(source));
    }

    public static PlayerDto fromObject(FederatedPlayer player) {
        return new PlayerDto(
                player.getId(),
                player.getName(),
                player.getSource().name(),
                player.getPlayer().map(canonical -> canonical.getId()).orElse(null),
                player.getPlayer().map(canonical -> canonical.getName()).orElse(null),
                List.of(),
                List.of(player.getSource().name())
        );
    }

    public static PlayerDto fromObject(PlayerSearchReadModel player) {
        List<FederatedDto> federatedPlayers = player.federatedPlayers().stream()
                .map(value -> new FederatedDto(value.id(), value.name(), value.license(),
                        value.source() == null ? null : value.source().name()))
                .toList();
        List<String> sources = federatedPlayers.stream()
                .map(FederatedDto::source)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String source = sources.size() == 1 ? sources.getFirst() : sources.isEmpty() ? null : "MULTIPLE";
        return new PlayerDto(player.id(), player.name(), source, player.canonicalPlayerId(),
                player.canonicalPlayerId() == null ? null : player.name(), federatedPlayers, sources);
    }

    public record FederatedDto(UUID id, String name, String license, String source) {
    }
}
