package org.cttelsamicsterrassa.data.core.domain.load.repository;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportResourceRepository {

    Optional<ImportResource> findById(UUID id);

    List<ImportResource> findAll();

    void save(ImportResource importResource);

    void deleteById(UUID id);
}
