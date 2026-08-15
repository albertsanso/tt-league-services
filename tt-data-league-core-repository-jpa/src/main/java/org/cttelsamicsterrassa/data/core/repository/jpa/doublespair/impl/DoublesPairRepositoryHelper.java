package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.model.DoublesPairJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DoublesPairRepositoryHelper extends JpaRepository<DoublesPairJPA, UUID> {
}
