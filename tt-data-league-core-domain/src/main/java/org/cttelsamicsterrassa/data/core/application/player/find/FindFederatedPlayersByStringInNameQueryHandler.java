package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public class FindFederatedPlayersByStringInNameQueryHandler
        extends DomainQueryHandler<FindFederatedPlayersByStringInNameQuery, List<PlayerSearchReadModel>> {

    private final FederatedPlayerRepository playerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    @Inject
    public FindFederatedPlayersByStringInNameQueryHandler(
            FederatedPlayerRepository playerRepository,
            PlayerSeasonRepository playerSeasonRepository) {
        this.playerRepository = playerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
    }

    @Override
    public DomainQueryResponse<List<PlayerSearchReadModel>> handle(
            FindFederatedPlayersByStringInNameQuery findPlayersByStringInNameQuery) {
        String search = findPlayersByStringInNameQuery.getStringToSearch();
        if (search == null || search.trim().length() < 2) {
            return DomainQueryResponse.failResponse(List.of());
        }
        List<FederatedPlayer> players = playerRepository.findAllFederatedPlayersBySourceAndFragmentsInName(
                findPlayersByStringInNameQuery.getSource(), List.of(search.trim().split("\\s+")));

        Map<UUID, List<FederatedPlayer>> groupedPlayers = players.stream()
                .collect(Collectors.groupingBy(player -> player.getPlayer().map(value -> value.getId()).orElse(player.getId())));

        List<PlayerSearchReadModel> results = groupedPlayers.entrySet().stream()
                .map(entry -> toReadModel(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PlayerSearchReadModel::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlayerSearchReadModel::name)
                        .thenComparing(PlayerSearchReadModel::id))
                .toList();
        return DomainQueryResponse.sucessResponse(results);
    }

    private PlayerSearchReadModel toReadModel(UUID resultId, List<FederatedPlayer> players) {
        FederatedPlayer first = players.stream()
                .sorted(Comparator.comparing(FederatedPlayer::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(FederatedPlayer::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(player -> player.getSource() == null ? null : player.getSource().name(),
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(FederatedPlayer::getId))
                .findFirst()
                .orElseThrow();
        UUID canonicalPlayerId = first.getPlayer().map(value -> value.getId()).orElse(null);
        String name = first.getPlayer().map(value -> value.getName()).orElse(first.getName());
        List<PlayerFederatedReadModel> federatedPlayers = players.stream()
                .sorted(Comparator.comparing(FederatedPlayer::getSource,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(FederatedPlayer::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(FederatedPlayer::getId))
                .map(player -> new PlayerFederatedReadModel(
                        player.getId(), player.getName(), player.getLicenseId(), player.getSource()))
                .toList();
        List<String> seasons = playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(
                        players.stream().map(FederatedPlayer::getId).toList()).stream()
                .map(PlayerSeason::getSeason)
                .distinct()
                .sorted(Comparator.comparing(Object::toString).reversed())
                .map(Object::toString)
                .toList();
        return new PlayerSearchReadModel(resultId, name, canonicalPlayerId, federatedPlayers, seasons);
    }
}
