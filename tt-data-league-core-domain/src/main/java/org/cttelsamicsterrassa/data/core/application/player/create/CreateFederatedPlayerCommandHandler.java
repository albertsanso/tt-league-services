package org.cttelsamicsterrassa.data.core.application.player.create;

import org.albertsanso.commons.command.*;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateFederatedPlayerCommandHandler extends DomainCommandHandler<CreateFederatedPlayerCommand> {
    private final FederatedPlayerRepository playerRepository;
    private final PlayerRepository canonicalPlayerRepository;

    public CreateFederatedPlayerCommandHandler(FederatedPlayerRepository playerRepository) {
        this(playerRepository, null);
    }

    @Inject
    public CreateFederatedPlayerCommandHandler(FederatedPlayerRepository playerRepository,
                                               PlayerRepository canonicalPlayerRepository) {
        this.playerRepository = playerRepository;
        this.canonicalPlayerRepository = canonicalPlayerRepository;
    }

    @Override public DomainCommandResponse handle(CreateFederatedPlayerCommand command) {
        if (command.getSource() == null) {
            return DomainCommandResponse.failResponse("Player source must not be null");
        }
        if (command.getFederatedPlayerName() == null || command.getFederatedPlayerName().isBlank()) {
            return DomainCommandResponse.failResponse("Player name must not be blank");
        }
        return playerRepository.findFederatedPlayerBySourceAndName(command.getSource(), command.getFederatedPlayerName())
            .map(p -> DomainCommandResponse.failResponse("FederatedPlayer with the same source and name already exists"))
            .orElseGet(() -> {
                Player canonicalPlayer = canonicalPlayerRepository == null
                    ? null
                    : canonicalPlayerRepository.findPlayerByExactName(command.getFederatedPlayerName())
                        .orElseGet(() -> {
                            Player created = Player.createNew(command.getFederatedPlayerName());
                            canonicalPlayerRepository.savePlayer(created);
                            return created;
                        });
                FederatedPlayer player = command.getFederatedPlayerId() == null
                    ? FederatedPlayer.createNew(command.getSource(), command.getFederatedPlayerName(), canonicalPlayer)
                    : FederatedPlayer.createExisting(command.getFederatedPlayerId(), command.getSource(),
                        command.getFederatedPlayerName(), canonicalPlayer);
                playerRepository.saveFederatedPlayer(player);
                return DomainCommandResponse.successResponse(player);
            });
    }
}
