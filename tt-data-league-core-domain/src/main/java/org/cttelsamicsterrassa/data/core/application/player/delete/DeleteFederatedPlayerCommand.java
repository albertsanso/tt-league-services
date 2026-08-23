package org.cttelsamicsterrassa.data.core.application.player.delete;
import org.albertsanso.commons.command.DomainCommand; import java.time.ZonedDateTime; import java.util.UUID;
public class DeleteFederatedPlayerCommand extends DomainCommand{private final UUID federatedPlayerId;public DeleteFederatedPlayerCommand(UUID id){super(ZonedDateTime.now(),id.toString());federatedPlayerId=id;}public UUID getFederatedPlayerId(){return federatedPlayerId;}}
