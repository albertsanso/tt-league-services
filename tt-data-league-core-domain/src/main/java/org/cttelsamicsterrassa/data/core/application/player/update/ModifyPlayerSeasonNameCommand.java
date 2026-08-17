package org.cttelsamicsterrassa.data.core.application.player.update;
import org.albertsanso.commons.command.DomainCommand; import java.time.ZonedDateTime; import java.util.UUID;
public class ModifyPlayerSeasonNameCommand extends DomainCommand { private final UUID playerSeasonId; private final String name;
 public ModifyPlayerSeasonNameCommand(ZonedDateTime time,String id,UUID playerSeasonId,String name){super(time,id);this.playerSeasonId=playerSeasonId;this.name=name;}
 public UUID getPlayerSeasonId(){return playerSeasonId;} public String getName(){return name;} }
