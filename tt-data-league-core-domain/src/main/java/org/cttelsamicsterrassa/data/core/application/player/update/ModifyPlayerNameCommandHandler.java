package org.cttelsamicsterrassa.data.core.application.player.update;
import org.albertsanso.commons.command.*; import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository; import javax.inject.*;
@Named public class ModifyPlayerNameCommandHandler extends DomainCommandHandler<ModifyPlayerNameCommand>{
 private final PlayerRepository repository; @Inject public ModifyPlayerNameCommandHandler(PlayerRepository r){repository=r;}
 @Override public DomainCommandResponse handle(ModifyPlayerNameCommand c){return repository.findPlayerById(c.getPlayerId()).map(p->{p.modifyName(c.getPlayerName());repository.savePlayer(p);return DomainCommandResponse.successResponse(p);}).orElseGet(()->DomainCommandResponse.successResponse("Player not found: "+c.getPlayerId()));}
}
