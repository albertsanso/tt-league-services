package org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper;

import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.model.ResourceJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ResourceToResourceJPAMapper implements Function<Resource, ResourceJPA> {
    @Override
    public ResourceJPA apply(Resource resource) {
        if (resource == null) {
            return null;
        }

        return new ResourceJPA(
                resource.getId(),
                resource.getName(),
                resource.getLogicPath(),
                resource.getPhysicalPath().toString());
    }
}
