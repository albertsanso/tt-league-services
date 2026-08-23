package org.cttelsamicsterrassa.data.core.application.player.update;
import org.albertsanso.commons.command.DomainCommand; import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import java.time.ZonedDateTime; import java.util.UUID;
public class ModifyFederatedPlayerNameCommand extends DomainCommand {
 private final UUID federatedPlayerId; private final String federatedPlayerName; private final ImportSource source;
 public ModifyFederatedPlayerNameCommand(ZonedDateTime occurredOn,String commandId,UUID federatedPlayerId,String federatedPlayerName,ImportSource source){super(occurredOn,commandId);this.federatedPlayerId=federatedPlayerId;this.federatedPlayerName=federatedPlayerName;this.source=source;}
 public UUID getFederatedPlayerId(){return federatedPlayerId;} public String getFederatedPlayerName(){return federatedPlayerName;} public ImportSource getSource(){return source;}
}
