package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceKeys;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.service.ResourceCreationService;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Named
public class ResourceRepositoryLoaderService {

    public static String IMPORT_FOLDER_TEMPLATE = "import-%s/%s";

    private final ResourceZipService resourceZipService;
    private final ResourceCreationService resourceCreationService;
    private final ImportResourceRepository importResourceRepository;
    private final ResourceRepository resourceRepository;

    @Inject
    public ResourceRepositoryLoaderService(ResourceZipService resourceZipService,
                                           ResourceCreationService resourceCreationService,
                                           ImportResourceRepository importResourceRepository,
                                           ResourceRepository resourceRepository) {
        this.resourceZipService = resourceZipService;
        this.resourceCreationService = resourceCreationService;
        this.importResourceRepository = importResourceRepository;
        this.resourceRepository = resourceRepository;
    }

    public void loadIntoRepository(ImportManifest importManifest) {

        Path importFolder = Path.of(resourceZipService.getFolderFromSetting());
        if (!Files.isDirectory(importFolder)) {
            throw new IllegalArgumentException("Configured import folder must exist: " + importFolder);
        }

        for (Map.Entry<String, List<String>> asset : importManifest.assets().entrySet()) {
            String assetType = asset.getKey();
            Path targetFolder = importFolder.resolve(
                    String.format(IMPORT_FOLDER_TEMPLATE,
                            importManifest.source().toLowerCase(Locale.ROOT),
                            assetType.toLowerCase(Locale.ROOT))
            );

            try {
                Files.createDirectories(targetFolder);
                for (String season : importManifest.seasons()) {
                    Path seasonFolder = targetFolder.resolve(season).normalize();
                    if (!seasonFolder.startsWith(targetFolder)) {
                        throw new IllegalArgumentException("Invalid season folder: " + season);
                    }
                    deleteRecursively(seasonFolder);
                    Files.createDirectory(seasonFolder);

                    moveSeasonContent(importManifest, asset.getValue(), season, seasonFolder,
                            resolveDestinationPathToRemoveForAssetType(assetType, season));
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unable to store extracted ZIP content", exception);
            }

            if ("ACTAS".equalsIgnoreCase(assetType)) {
                createResourcesAndStartProcessing(importManifest, assetType, targetFolder);
            }
        }
    }

    private Path resolveDestinationPathToRemoveForAssetType(String assetType, String season) {
        if ("TEAMS".equalsIgnoreCase(assetType)) {
            return Path.of("equipos-json/");
        } else {
            return Path.of("actas-json/"+season);
        }
    }

    private void moveSeasonContent(ImportManifest importManifest,
                                   List<String> files,
                                   String season,
                                   Path seasonFolder,
                                   Path pathToRemove) throws IOException {
        Path extractedSeasonFolder = importManifest.extractionFolder().resolve(season).normalize();
        if (files.isEmpty() && Files.isDirectory(extractedSeasonFolder)) {
            moveDirectoryContents(extractedSeasonFolder, seasonFolder);
            return;
        }

        for (String file : files) {
            Path extractedFile = importManifest.extractionFolder().resolve(file).normalize();
            if (!extractedFile.startsWith(importManifest.extractionFolder())
                    || !Files.isRegularFile(extractedFile)) {
                throw new IllegalArgumentException("manifest.json references a missing file: " + file);
            }
            Path relativeFile = importManifest.extractionFolder().relativize(extractedFile);
            if (relativeFile.startsWith(season)) {
                relativeFile = relativeFile.subpath(1, relativeFile.getNameCount());
            }
            Path relativeFileToRemove = pathToRemove.relativize(relativeFile).normalize();
            Path destination = seasonFolder.resolve(relativeFileToRemove).normalize();

            Files.createDirectories(destination.getParent());
            Files.move(extractedFile, destination);
        }
    }

    private void createResourcesAndStartProcessing(ImportManifest importManifest,
                                                   String assetType,
                                                   Path targetFolder) {
        Resource resolvedResource = createOrGetResource(importManifest, assetType, targetFolder);
        importManifest.seasons()
            .forEach(season -> {
                ImportResource importResource = createOrGetImportResourceForResource(
                        resolvedResource, importManifest, assetType, season);
                ImportResourceStatus.getAllFinishedStatuses().forEach(status -> {
                    if (importResource.getStatus() == status) {
                        importResource.setPending();
                        importResourceRepository.save(importResource);
                    }
                });
            });
    }

    private Resource createOrGetResource(ImportManifest importManifest,
                                         String assetType,
                                         Path targetFolder) {
        String logicalPath = ResourceKeys.dataImportKey(importManifest.source(), assetType);
        return resourceRepository.findByLogicPathAndName(logicalPath, assetType)
                .orElseGet(() ->
                        resourceCreationService.createNewFromImportManifestAndFolder(
                                importManifest, assetType, targetFolder));
    }

    private ImportResource createOrGetImportResourceForResource(Resource resource,
                                                                ImportManifest importManifest,
                                                                String assetType,
                                                                String season) {
        return importResourceRepository.findBySourceAndTypeAndSeason(importManifest.source(), assetType, season)
                .orElseGet(() -> {
                    ImportResource importResource = ImportResource.createNew(
                            resource,
                            Optional.empty(),
                            mapResourceType(assetType),
                            ZonedDateTime.now(),
                            Optional.empty(),
                            Season.fromFormatted(season),
                            mapImportSource(importManifest.source())
                    );
                    importResourceRepository.save(importResource);
                    return importResource;
                });
    }

    private ImportSource mapImportSource(String source) {
        if ("RFETM".equalsIgnoreCase(source)) {
            return ImportSource.RFETM;
        } else if ("FCTT".equalsIgnoreCase(source)) {
            return ImportSource.FCTT;
        } else  if ("BCNESA".equalsIgnoreCase(source)) {
            return ImportSource.BCNESA;
        } else {
            throw new IllegalArgumentException("Invalid source in manifest.json: " + source);
        }
    }

    private ResourceType mapResourceType(String assetType) {
        if ("ACTAS".equalsIgnoreCase(assetType)) {
            return ResourceType.ACTAS;
        } else if ("TEAMS".equalsIgnoreCase(assetType)) {
            return ResourceType.TEAMS;
        } else {
            throw new IllegalArgumentException("Invalid asset_type in manifest.json: " + assetType);
        }
    }

    private void moveDirectoryContents(Path sourceFolder, Path targetFolder) throws IOException {
        try (var entries = Files.walk(sourceFolder)) {
            for (Path entry : entries
                    .filter(path -> !path.equals(sourceFolder))
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Path destination = targetFolder.resolve(sourceFolder.relativize(entry));
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.move(entry, destination);
                }
            }
        }
    }

    private void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (var entries = Files.walk(folder)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }
}
