package org.cttelsamicsterrassa.data.core.repository.jpa.setscore.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.model.SetScoreJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SetScoreRepositoryHelper extends JpaRepository<SetScoreJPA, UUID> {
}
