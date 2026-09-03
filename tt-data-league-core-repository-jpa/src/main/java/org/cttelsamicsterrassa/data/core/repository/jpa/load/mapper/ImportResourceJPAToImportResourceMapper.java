package org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportResourceJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.mapper.ResourceJPAToResourceMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.Optional;

@Component
@AllArgsConstructor
public class ImportResourceJPAToImportResourceMapper implements Function<ImportResourceJPA, ImportResource> {
    private final ResourceJPAToResourceMapper resourceJPAToResourceMapper;

    @Override
    public ImportResource apply(ImportResourceJPA importResourceJPA) {
        if (importResourceJPA == null) {
            return null;
        }

        return ImportResource.createExisting(
                importResourceJPA.getId(),
                resourceJPAToResourceMapper.apply(importResourceJPA.getResource()),
                Optional.ofNullable(importResourceJPA.getValid()),
                importResourceJPA.getType(),
                importResourceJPA.getCreated(),
                Optional.ofNullable(importResourceJPA.getLastProcessedDate()),
                Season.fromFormatted(importResourceJPA.getSeason()),
                ImportSource.valueOf(importResourceJPA.getSource().name()),
                importResourceJPA.getStatus()
        );
    }
}
