package org.cttelsamicsterrassa.data.core.domain.resource.repository;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    Optional<Resource> findById(UUID id);
    Optional<Resource> findByTypeAndName(ResourceType type, String name);

    List<Resource> findAll();
    List<Resource> findAllByType(ResourceType type);

    void deleteById(UUID id);
    void save(Resource resource);
}
