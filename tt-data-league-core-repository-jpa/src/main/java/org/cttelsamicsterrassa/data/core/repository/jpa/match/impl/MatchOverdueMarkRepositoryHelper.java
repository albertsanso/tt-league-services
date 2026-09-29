package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchOverdueMarkJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchOverdueMarkRepositoryHelper extends JpaRepository<MatchOverdueMarkJPA, UUID> {
}