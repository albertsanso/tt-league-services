package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerJPAToPlayerMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerToPlayerJPAMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class PlayerRepositoryJpa implements PlayerRepository {

    private final PlayerRepositoryHelper playerRepositoryHelper;
    private final PlayerJPAToPlayerMapper playerJPAToPlayerMapper;
    private final PlayerToPlayerJPAMapper playerToPlayerJPAMapper;

    @Override
    public Optional<Player> findPlayerById(UUID id) {
        return playerRepositoryHelper.findById(id).map(playerJPAToPlayerMapper);
    }

    @Override
    public Optional<Player> findPlayerByName(String name) {
        return playerRepositoryHelper.findByName(name).map(playerJPAToPlayerMapper);
    }

    @Override
    public Optional<Player> findPlayerBySourceAndName(ImportSource source, String name) {
        Source jpaSource = source != null ? Source.valueOf(source.name()) : null;
        return playerRepositoryHelper.findFirstBySourceAndName(jpaSource, name).map(playerJPAToPlayerMapper);
    }

    @Override
    public void savePlayer(Player player) {
        playerRepositoryHelper.save(playerToPlayerJPAMapper.apply(player));
    }
}
