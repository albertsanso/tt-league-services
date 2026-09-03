package org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportResourceJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper.ResourceToResourceJPAMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@AllArgsConstructor
public class ImportResourceToImportResourceJPAMapper implements Function<ImportResource, ImportResourceJPA> {
    private final ResourceToResourceJPAMapper resourceToResourceJPAMapper;

    @Override
    public ImportResourceJPA apply(ImportResource importResource) {
        if (importResource == null) {
            return null;
        }

        return new ImportResourceJPA(
                importResource.getId(),
                resourceToResourceJPAMapper.apply(importResource.getResource()),
                importResource.getValid().orElse(null),
                importResource.getType(),
                importResource.getCreated(),
                importResource.getLastProcessedDate().orElse(null),
                importResource.getSeason().toString(),
                Source.valueOf(importResource.getSource().name()),
                importResource.getStatus()
        );
    }
}
