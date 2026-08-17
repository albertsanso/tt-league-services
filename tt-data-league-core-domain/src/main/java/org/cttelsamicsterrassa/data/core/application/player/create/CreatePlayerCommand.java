package org.cttelsamicsterrassa.data.core.application.player.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import java.time.ZonedDateTime;
import java.util.UUID;

public class CreatePlayerCommand extends DomainCommand {
    private final UUID playerId;
    private final String playerName;
    private final ImportSource source;

    public CreatePlayerCommand(ZonedDateTime occurredOn, String commandId, UUID playerId, String playerName, ImportSource source) {
        super(occurredOn, commandId);
        this.playerId = playerId;
        this.playerName = playerName;
        this.source = source;
    }
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public ImportSource getSource() { return source; }
}
