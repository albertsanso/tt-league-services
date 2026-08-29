package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonJPAToPlayerSeasonMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonToPlayerSeasonJPAMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@Component
@AllArgsConstructor
public class PlayerSeasonRepositoryJpa implements PlayerSeasonRepository {

    private final PlayerSeasonRepositoryHelper playerSeasonRepositoryHelper;
    private final PlayerSeasonJPAToPlayerSeasonMapper playerSeasonJPAToPlayerSeasonMapper;
    private final PlayerSeasonToPlayerSeasonJPAMapper playerSeasonToPlayerSeasonJPAMapper;

    @Override
    public Optional<PlayerSeason> findPlayerSeasonById(UUID id) {
        return playerSeasonRepositoryHelper.findById(id).map(playerSeasonJPAToPlayerSeasonMapper);
    }

    @Override
    public Optional<PlayerSeason> findPlayerSeasonBySourceLicenseAndSeason(ImportSource source, String license, Season season) {
        Source jpaSource = mapFromImportSourceToSource(source);
        return playerSeasonRepositoryHelper.findBySourceAndLicenseIdAndSeason(jpaSource, license, season.toString())
                .map(playerSeasonJPAToPlayerSeasonMapper);
    }

    @Override
    public List<PlayerSeason> findAllPlayerSeasonsBySource(ImportSource source) {
        return playerSeasonRepositoryHelper.findAllBySource(mapFromImportSourceToSource(source))
                .stream()
                .map(playerSeasonJPAToPlayerSeasonMapper)
                .toList();
    }

    @Override
    public List<PlayerSeason> findAllPlayerSeasonsByFederatedPlayerIds(Collection<UUID> federatedPlayerIds) {
        if (federatedPlayerIds == null || federatedPlayerIds.isEmpty()) {
            return List.of();
        }
        return playerSeasonRepositoryHelper.findAllByFederatedPlayerIds(federatedPlayerIds).stream()
                .map(playerSeasonJPAToPlayerSeasonMapper).toList();
    }

    @Override
    public List<PlayerSeason> findAllPlayerSeasonsByTeamIdsAndSource(
            Collection<UUID> teamIds,
            ImportSource source) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return playerSeasonRepositoryHelper.findAllByTeamIdsAndSource(
                        teamIds, mapFromImportSourceToSource(source))
                .stream()
                .map(playerSeasonJPAToPlayerSeasonMapper)
                .toList();
    }

    @Override
    public Map<UUID, List<String>> findAllPlayerSeasonCompetitionsByTeamIdsAndSource(
            Collection<UUID> teamIds,
            ImportSource source) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> competitionsByPlayerSeason = new LinkedHashMap<>();
        playerSeasonRepositoryHelper.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(
                        teamIds, mapFromImportSourceToSource(source))
                .forEach(projection -> competitionsByPlayerSeason
                        .computeIfAbsent(projection.getPlayerSeasonId(), ignored -> new ArrayList<>())
                        .add(projection.getCompetition()));
        return competitionsByPlayerSeason.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().distinct().toList()));
    }

    @Override
    public void savePlayerSeason(PlayerSeason playerSeason) {
        playerSeasonRepositoryHelper.save(playerSeasonToPlayerSeasonJPAMapper.apply(playerSeason));
    }

    @Override
    public void deletePlayerSeasonById(UUID id) {
        playerSeasonRepositoryHelper.deleteById(id);
    }

    private static Source mapFromImportSourceToSource(ImportSource source) {
        if (source == null) {
            return null;
        }
        return Source.valueOf(source.name());
    }
}
