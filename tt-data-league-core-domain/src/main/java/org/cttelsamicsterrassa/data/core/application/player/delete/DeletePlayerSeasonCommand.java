package org.cttelsamicsterrassa.data.core.application.player.delete;
import org.albertsanso.commons.command.DomainCommand; import java.time.ZonedDateTime; import java.util.UUID;
public class DeletePlayerSeasonCommand extends DomainCommand{private final UUID playerSeasonId;public DeletePlayerSeasonCommand(UUID id){super(ZonedDateTime.now(),id.toString());playerSeasonId=id;}public UUID getPlayerSeasonId(){return playerSeasonId;}}
