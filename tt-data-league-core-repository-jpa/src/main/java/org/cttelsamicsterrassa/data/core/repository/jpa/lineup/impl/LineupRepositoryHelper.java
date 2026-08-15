package org.cttelsamicsterrassa.data.core.repository.jpa.lineup.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.model.LineupJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LineupRepositoryHelper extends JpaRepository<LineupJPA, UUID> {
    List<LineupJPA> findAllByMatch_Id(UUID matchId);
}
