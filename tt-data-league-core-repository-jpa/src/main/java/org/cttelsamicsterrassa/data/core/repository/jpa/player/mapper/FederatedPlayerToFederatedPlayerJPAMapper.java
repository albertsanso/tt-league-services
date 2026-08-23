package org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.FederatedPlayerJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class FederatedPlayerToFederatedPlayerJPAMapper implements Function<FederatedPlayer, FederatedPlayerJPA> {
    @Override
    public FederatedPlayerJPA apply(FederatedPlayer player) {
        if (player == null) {
            return null;
        }
        FederatedPlayerJPA playerJPA = new FederatedPlayerJPA();
        playerJPA.setId(player.getId());
        playerJPA.setSource(player.getSource() != null ? Source.valueOf(player.getSource().name()) : null);
        playerJPA.setName(player.getName());
        return playerJPA;
    }
}
