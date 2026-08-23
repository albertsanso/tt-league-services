package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindFederatedPlayersByStringInNameQueryHandler extends DomainQueryHandler<FindFederatedPlayersByStringInNameQuery, List<FederatedPlayer>> {

    private final FederatedPlayerRepository playerRepository;

    @Inject
    public FindFederatedPlayersByStringInNameQueryHandler(FederatedPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public DomainQueryResponse<List<FederatedPlayer>> handle(FindFederatedPlayersByStringInNameQuery findPlayersByStringInNameQuery) {
        return DomainQueryResponse.sucessResponse(
            playerRepository.findAllFederatedPlayersByFragmentsInName(
                    List.of(findPlayersByStringInNameQuery.getStringToSearch().split(" ")))
                .stream().toList()
        );
    }
}
