package org.cttelsamicsterrassa.data.core.repository.jpa.resource.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
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
    public Optional<Resource> findByTypeAndName(ResourceType type, String name) {
        return resourceRepositoryHelper.findByTypeAndName(type, name).map(resourceJPAToResourceMapper);
    }

    @Override
    public List<Resource> findAll() {
        return resourceRepositoryHelper.findAll().stream()
                .map(resourceJPAToResourceMapper)
                .toList();
    }

    @Override
    public List<Resource> findAllByType(ResourceType type) {
        return resourceRepositoryHelper.findAllByType(type).stream()
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
