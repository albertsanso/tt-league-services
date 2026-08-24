package org.cttelsamicsterrassa.data.core.application.player.create;

import org.albertsanso.commons.command.*;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import javax.inject.Inject; import javax.inject.Named;

@Named
public class CreatePlayerSeasonCommandHandler extends DomainCommandHandler<CreatePlayerSeasonCommand> {
    private final PlayerSeasonRepository repository;
    @Inject public CreatePlayerSeasonCommandHandler(PlayerSeasonRepository repository){this.repository=repository;}
    @Override public DomainCommandResponse handle(CreatePlayerSeasonCommand c) {
        return repository.findPlayerSeasonBySourceLicenseAndSeason(c.getSource(),c.getLicense(),c.getSeason())
            .map(x->DomainCommandResponse.failResponse("Player season with the same source, license and season already exists"))
            .orElseGet(()->{var ps=PlayerSeason.createNew(c.getSource(),c.getName(),c.getLicense(),c.getFederatedPlayer(),c.getSeason()); repository.savePlayerSeason(ps); return DomainCommandResponse.successResponse(ps);});
    }
}
