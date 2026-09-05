package org.cttelsamicsterrassa.data.core.domain.resource.service;

import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceKeys;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;

@Named
public class ResourceCreationService {

    private final ResourceRepository resourceRepository;

    @Inject
    public ResourceCreationService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public Resource createNewFromImportManifestAndFolder(ImportManifest importManifest,
                                                         String assetType,
                                                         Path folder) {
        String logicalPath = ResourceKeys.dataImportKey(importManifest.source(), assetType);
        Resource resource = Resource.createNew(
                assetType,
                logicalPath,
                folder);
        resourceRepository.save(resource);
        return resource;
    }
}
