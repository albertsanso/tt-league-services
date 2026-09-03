package org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.model.ResourceJPA;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.Function;

@Component
public class ResourceJPAToResourceMapper implements Function<ResourceJPA, Resource> {
    @Override
    public Resource apply(ResourceJPA resourceJPA) {
        if (resourceJPA == null) {
            return null;
        }

        return Resource.createExisting(
                resourceJPA.getId(),
                resourceJPA.getName(),
                resourceJPA.getLogicPath(),
                Path.of(resourceJPA.getPhysicalPath()));
    }
}
