package org.cttelsamicsterrassa.data.core.repository.jpa.load.impl;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportResourceJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportResourceRepositoryHelper extends JpaRepository<ImportResourceJPA, UUID> {

    Optional<ImportResourceJPA> findBySourceAndTypeAndSeason(Source source, ResourceType type, String season);
    Collection<ImportResourceJPA> findBySourceAndTypeOrderByCreatedDesc(Source source, ResourceType type);
    List<ImportResourceJPA> findAllByStatus(ImportResourceStatus status);

    Collection<ImportResourceJPA> findBySource(Source source);
}
