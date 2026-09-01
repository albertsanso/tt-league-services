package org.cttelsamicsterrassa.data.core.repository.jpa.load.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportResourceJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportResourceRepositoryHelper extends JpaRepository<ImportResourceJPA, UUID> {
}
