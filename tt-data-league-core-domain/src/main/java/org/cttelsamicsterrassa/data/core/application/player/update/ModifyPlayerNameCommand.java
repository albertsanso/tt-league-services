package org.cttelsamicsterrassa.data.core.application.player.update;
import org.albertsanso.commons.command.DomainCommand; import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import java.time.ZonedDateTime; import java.util.UUID;
public class ModifyPlayerNameCommand extends DomainCommand {
 private final UUID playerId; private final String playerName; private final ImportSource source;
 public ModifyPlayerNameCommand(ZonedDateTime occurredOn,String commandId,UUID playerId,String playerName,ImportSource source){super(occurredOn,commandId);this.playerId=playerId;this.playerName=playerName;this.source=source;}
 public UUID getPlayerId(){return playerId;} public String getPlayerName(){return playerName;} public ImportSource getSource(){return source;}
}
