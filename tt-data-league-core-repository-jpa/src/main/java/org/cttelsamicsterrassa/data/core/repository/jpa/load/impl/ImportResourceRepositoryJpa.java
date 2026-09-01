package org.cttelsamicsterrassa.data.core.repository.jpa.load.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper.ImportResourceJPAToImportResourceMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper.ImportResourceToImportResourceJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ImportResourceRepositoryJpa implements ImportResourceRepository {
    private final ImportResourceRepositoryHelper importResourceRepositoryHelper;
    private final ImportResourceJPAToImportResourceMapper importResourceJPAToImportResourceMapper;
    private final ImportResourceToImportResourceJPAMapper importResourceToImportResourceJPAMapper;

    @Override
    public Optional<ImportResource> findById(UUID id) {
        return importResourceRepositoryHelper.findById(id).map(importResourceJPAToImportResourceMapper);
    }

    @Override
    public List<ImportResource> findAll() {
        return importResourceRepositoryHelper.findAll().stream()
                .map(importResourceJPAToImportResourceMapper)
                .toList();
    }

    @Override
    public void save(ImportResource importResource) {
        importResourceRepositoryHelper.save(importResourceToImportResourceJPAMapper.apply(importResource));
    }

    @Override
    public void deleteById(UUID id) {
        importResourceRepositoryHelper.deleteById(id);
    }
}
