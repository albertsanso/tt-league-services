package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindPlayersByStringInNameQueryHandler extends DomainQueryHandler<FindPlayersByStringInNameQuery, List<Player>> {

    private final PlayerRepository playerRepository;

    @Inject
    public FindPlayersByStringInNameQueryHandler(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public DomainQueryResponse<List<Player>> handle(FindPlayersByStringInNameQuery findPlayersByStringInNameQuery) {
        return DomainQueryResponse.sucessResponse(
            playerRepository.findAllPlayersByFragmentsInName(
                    List.of(findPlayersByStringInNameQuery.getStringToSearch().split(" ")))
                .stream().toList()
        );
    }
}
