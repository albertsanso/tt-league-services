package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepositoryHelper extends JpaRepository<PlayerJPA, UUID>, JpaSpecificationExecutor<PlayerJPA> {
    Optional<PlayerJPA> findByName(String name);
}
