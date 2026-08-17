package org.cttelsamicsterrassa.data.core.application.player.create;

import org.albertsanso.commons.command.*;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreatePlayerCommandHandler extends DomainCommandHandler<CreatePlayerCommand> {
    private final PlayerRepository playerRepository;
    @Inject public CreatePlayerCommandHandler(PlayerRepository playerRepository) { this.playerRepository = playerRepository; }
    @Override public DomainCommandResponse handle(CreatePlayerCommand command) {
        return playerRepository.findPlayerBySourceAndName(command.getSource(), command.getPlayerName())
            .map(p -> DomainCommandResponse.failResponse("Player with the same source and name already exists"))
            .orElseGet(() -> {
                Player player = command.getPlayerId() == null
                    ? Player.createNew(command.getSource(), command.getPlayerName())
                    : Player.createExisting(command.getPlayerId(), command.getSource(), command.getPlayerName());
                playerRepository.savePlayer(player);
                return DomainCommandResponse.successResponse(player);
            });
    }
}
