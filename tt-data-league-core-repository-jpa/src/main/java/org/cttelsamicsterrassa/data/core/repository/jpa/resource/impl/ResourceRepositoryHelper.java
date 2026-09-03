package org.cttelsamicsterrassa.data.core.repository.jpa.resource.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.resource.model.ResourceJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepositoryHelper extends JpaRepository<ResourceJPA, UUID> {
    Optional<ResourceJPA> findByLogicPathAndName(String logicPath, String name);

    List<ResourceJPA> findAllByLogicPath(String logicPath);
}
