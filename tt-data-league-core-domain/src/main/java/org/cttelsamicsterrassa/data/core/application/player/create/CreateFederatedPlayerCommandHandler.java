package org.cttelsamicsterrassa.data.core.application.player.create;

import org.albertsanso.commons.command.*;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateFederatedPlayerCommandHandler extends DomainCommandHandler<CreateFederatedPlayerCommand> {
    private final FederatedPlayerRepository playerRepository;
    @Inject public CreateFederatedPlayerCommandHandler(FederatedPlayerRepository playerRepository) { this.playerRepository = playerRepository; }
    @Override public DomainCommandResponse handle(CreateFederatedPlayerCommand command) {
        return playerRepository.findFederatedPlayerBySourceAndName(command.getSource(), command.getFederatedPlayerName())
            .map(p -> DomainCommandResponse.failResponse("FederatedPlayer with the same source and name already exists"))
            .orElseGet(() -> {
                FederatedPlayer player = command.getFederatedPlayerId() == null
                    ? FederatedPlayer.createNew(command.getSource(), command.getFederatedPlayerName())
                    : FederatedPlayer.createExisting(command.getFederatedPlayerId(), command.getSource(), command.getFederatedPlayerName());
                playerRepository.saveFederatedPlayer(player);
                return DomainCommandResponse.successResponse(player);
            });
    }
}
