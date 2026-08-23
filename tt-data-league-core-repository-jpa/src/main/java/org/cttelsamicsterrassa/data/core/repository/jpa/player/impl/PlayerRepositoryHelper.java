package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepositoryHelper extends JpaRepository<PlayerJPA, UUID> {
    Optional<PlayerJPA> findByName(String name);
}
