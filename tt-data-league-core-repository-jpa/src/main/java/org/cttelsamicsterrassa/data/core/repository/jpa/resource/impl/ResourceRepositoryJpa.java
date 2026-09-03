package org.cttelsamicsterrassa.data.core.repository.jpa.resource.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper.ResourceJPAToResourceMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper.ResourceToResourceJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ResourceRepositoryJpa implements ResourceRepository {
    private final ResourceRepositoryHelper resourceRepositoryHelper;
    private final ResourceJPAToResourceMapper resourceJPAToResourceMapper;
    private final ResourceToResourceJPAMapper resourceToResourceJPAMapper;

    @Override
    public Optional<Resource> findById(UUID id) {
        return resourceRepositoryHelper.findById(id).map(resourceJPAToResourceMapper);
    }

    @Override
    public Optional<Resource> findByLogicPathAndName(String logicPath, String name) {
        return resourceRepositoryHelper.findByLogicPathAndName(logicPath, name).map(resourceJPAToResourceMapper);
    }

    @Override
    public List<Resource> findAll() {
        return resourceRepositoryHelper.findAll().stream()
                .map(resourceJPAToResourceMapper)
                .toList();
    }

    @Override
    public List<Resource> findAllByLogicPath(String logicPath) {
        return resourceRepositoryHelper.findAllByLogicPath(logicPath).stream()
                .map(resourceJPAToResourceMapper)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        resourceRepositoryHelper.deleteById(id);
    }

    @Override
    public void save(Resource resource) {
        resourceRepositoryHelper.save(resourceToResourceJPAMapper.apply(resource));
    }
}
