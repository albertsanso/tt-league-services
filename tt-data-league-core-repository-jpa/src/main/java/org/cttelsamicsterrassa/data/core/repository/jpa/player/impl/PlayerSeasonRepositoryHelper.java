package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerSeasonRepositoryHelper extends JpaRepository<PlayerSeasonJPA, UUID> {
    Optional<PlayerSeasonJPA> findBySourceAndLicenseAndSeason(Source source, String license, String season);
}
