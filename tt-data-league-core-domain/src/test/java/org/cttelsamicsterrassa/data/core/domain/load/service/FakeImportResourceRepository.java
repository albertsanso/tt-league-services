package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link ImportResourceRepository} test double keeping resources in a list and counting saves. */
public final class FakeImportResourceRepository implements ImportResourceRepository {
    private final List<ImportResource> resources = new ArrayList<>();
    private int saveCount;

    public static ImportResource actasResource(ImportSource source, String season, ImportResourceStatus status) {
        Resource resource = Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/actas",
                Path.of("import", "actas"));
        return ImportResource.createExisting(UUID.randomUUID(), resource, Optional.empty(), ResourceType.ACTAS,
                ZonedDateTime.now(), Optional.empty(), Season.fromFormatted(season), source, status);
    }

    public ImportResource add(ImportResource resource) {
        resources.add(resource);
        return resource;
    }

    public int saveCount() {
        return saveCount;
    }

    @Override
    public Optional<ImportResource> findById(UUID id) {
        return resources.stream().filter(resource -> resource.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<ImportResource> findBySourceAndTypeAndSeason(String source, String type, String season) {
        return Optional.empty();
    }

    @Override
    public List<ImportResource> findAllPendingImports() {
        return List.of();
    }

    @Override
    public List<ImportResource> findBySourceAndType(String source, String type) {
        return List.of();
    }

    @Override
    public List<ImportResource> findBySource(String source) {
        return List.of();
    }

    @Override
    public List<ImportResource> findAll() {
        return List.copyOf(resources);
    }

    @Override
    public void save(ImportResource importResource) {
        saveCount++;
    }

    @Override
    public void deleteById(UUID id) {
        resources.removeIf(resource -> resource.getId().equals(id));
    }
}
