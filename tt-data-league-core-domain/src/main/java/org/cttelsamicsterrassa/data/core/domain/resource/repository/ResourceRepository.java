package org.cttelsamicsterrassa.data.core.domain.resource.repository;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    Optional<Resource> findById(UUID id);
    Optional<Resource> findByLogicPathAndName(String logicPath, String name);

    List<Resource> findAll();
    List<Resource> findAllByLogicPath(String logicPath);

    void deleteById(UUID id);
    void save(Resource resource);
}
