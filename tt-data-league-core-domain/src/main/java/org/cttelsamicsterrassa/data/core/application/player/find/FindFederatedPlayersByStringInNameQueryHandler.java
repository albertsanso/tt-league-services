package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
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
        String search = findPlayersByStringInNameQuery.getStringToSearch();
        if (search == null || search.trim().length() < 2) {
            return DomainQueryResponse.failResponse(List.of());
        }
        return DomainQueryResponse.sucessResponse(
            playerRepository.findAllFederatedPlayersBySourceAndFragmentsInName(
                    findPlayersByStringInNameQuery.getSource(), List.of(search.trim().split("\\s+")))
                .stream()
                .sorted(Comparator.comparing(FederatedPlayer::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(FederatedPlayer::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(player -> player.getSource() == null ? null : player.getSource().name(),
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(FederatedPlayer::getId))
                .toList()
        );
    }
}
