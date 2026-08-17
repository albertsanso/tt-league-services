package org.cttelsamicsterrassa.data.core.application.player.delete;
import org.albertsanso.commons.command.DomainCommand; import java.time.ZonedDateTime; import java.util.UUID;
public class DeletePlayerCommand extends DomainCommand{private final UUID playerId;public DeletePlayerCommand(UUID id){super(ZonedDateTime.now(),id.toString());playerId=id;}public UUID getPlayerId(){return playerId;}}
