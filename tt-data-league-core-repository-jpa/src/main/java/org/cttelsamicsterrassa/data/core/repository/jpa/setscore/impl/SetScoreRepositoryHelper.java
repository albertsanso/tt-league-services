package org.cttelsamicsterrassa.data.core.repository.jpa.setscore.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.model.SetScoreJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetScoreRepositoryHelper extends JpaRepository<SetScoreJPA, UUID> {
    @Query("select s from SetScoreJPA s where s.game.id in :gameIds order by s.game.id asc, s.setNumber asc")
    List<SetScoreJPA> findAllByGameIds(@Param("gameIds") Collection<UUID> gameIds);
}
