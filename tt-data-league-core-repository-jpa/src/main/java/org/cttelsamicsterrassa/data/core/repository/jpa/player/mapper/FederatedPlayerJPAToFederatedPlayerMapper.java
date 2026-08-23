package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.FederatedPlayerJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class FederatedPlayerJPAToFederatedPlayerMapper implements Function<FederatedPlayerJPA, FederatedPlayer> {
    @Override
    public FederatedPlayer apply(FederatedPlayerJPA playerJPA) {
        if (playerJPA == null) {
            return null;
        }
        return FederatedPlayer.createExisting(
                playerJPA.getId(),
                playerJPA.getSource() != null ? ImportSource.valueOf(playerJPA.getSource().name()) : null,
                playerJPA.getName()
        );
    }
}
