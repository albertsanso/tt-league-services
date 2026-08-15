package org.cttelsamicsterrassa.data.core.domain.player.repository;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link PlayerSeason}, a player's registration for one season. The natural
 * key is the federation licence within a season, scoped to the source federation: licences are
 * independently assigned by source, so the same value can name different players.
 */
public interface PlayerSeasonRepository {
    Optional<PlayerSeason> findPlayerSeasonById(UUID id);
    Optional<PlayerSeason> findPlayerSeasonByLicenseAndSeason(ImportSource source, String license, Season season);
    void savePlayerSeason(PlayerSeason playerSeason);
}
