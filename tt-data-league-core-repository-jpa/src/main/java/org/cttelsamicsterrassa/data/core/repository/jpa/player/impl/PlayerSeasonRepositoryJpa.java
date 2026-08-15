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
import java.util.UUID;

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
    public Optional<PlayerSeason> findPlayerSeasonByLicenseAndSeason(ImportSource source, String license, Season season) {
        Source jpaSource = source != null ? Source.valueOf(source.name()) : null;
        return playerSeasonRepositoryHelper.findBySourceAndLicenseAndSeason(jpaSource, license, season.toString())
                .map(playerSeasonJPAToPlayerSeasonMapper);
    }

    @Override
    public void savePlayerSeason(PlayerSeason playerSeason) {
        playerSeasonRepositoryHelper.save(playerSeasonToPlayerSeasonJPAMapper.apply(playerSeason));
    }
}
