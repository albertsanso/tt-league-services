package org.cttelsamicsterrassa.data.core.application.player.update;
import org.albertsanso.commons.command.*; import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository; import javax.inject.*;
@Named public class ModifyFederatedPlayerNameCommandHandler extends DomainCommandHandler<ModifyFederatedPlayerNameCommand>{
 private final FederatedPlayerRepository repository; @Inject public ModifyFederatedPlayerNameCommandHandler(FederatedPlayerRepository r){repository=r;}
 @Override public DomainCommandResponse handle(ModifyFederatedPlayerNameCommand c){return repository.findFederatedPlayerById(c.getFederatedPlayerId()).map(p->{p.modifyName(c.getFederatedPlayerName());repository.saveFederatedPlayer(p);return DomainCommandResponse.successResponse(p);}).orElseGet(()->DomainCommandResponse.successResponse("FederatedPlayer not found: "+c.getFederatedPlayerId()));}
}
