package org.cttelsamicsterrassa.data.core.repository.jpa.game.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.game.model.GameJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface GameRepositoryHelper extends JpaRepository<GameJPA, UUID> {
    List<GameJPA> findAllByMatch_IdOrderByGameNumberAsc(UUID matchId);
    List<GameJPA> findAllByMatch_IdInOrderByMatch_IdAscGameNumberAsc(Collection<UUID> matchIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from GameJPA g where g.match.id in :matchIds")
    int deleteAllByMatchIds(@Param("matchIds") Collection<UUID> matchIds);
}
