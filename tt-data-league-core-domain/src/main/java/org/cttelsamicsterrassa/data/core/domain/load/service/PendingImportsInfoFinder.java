package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class PendingImportsInfoFinder {
    private final ImportResourceRepository importResourceRepository;

    @Inject
    public PendingImportsInfoFinder(ImportResourceRepository importResourceRepository) {
        this.importResourceRepository = importResourceRepository;
    }

    public List<ImportResource> getPendingImports() {
        return importResourceRepository.findAllPendingImports();
    }
}
