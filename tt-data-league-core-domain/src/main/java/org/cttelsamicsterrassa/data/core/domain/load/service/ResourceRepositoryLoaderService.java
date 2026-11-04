package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceKeys;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.service.ResourceCreationService;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Named
public class ResourceRepositoryLoaderService {

    public static String IMPORT_FOLDER_TEMPLATE = "import-%s/%s";

    public static String UPLOAD_ROLLBACK_FOLDER_TEMPLATE = "upload-rollback/%s/%s";

    private static final String ACTAS_ASSET = "ACTAS";

    private static final Logger LOGGER = Logger.getLogger(ResourceRepositoryLoaderService.class.getName());

    private final ResourceZipService resourceZipService;
    private final ResourceCreationService resourceCreationService;
    private final ImportResourceRepository importResourceRepository;
    private final ResourceRepository resourceRepository;
    private final PublishedActaCounter publishedActaCounter;

    @Inject
    public ResourceRepositoryLoaderService(ResourceZipService resourceZipService,
                                           ResourceCreationService resourceCreationService,
                                           ImportResourceRepository importResourceRepository,
                                           ResourceRepository resourceRepository,
                                           ObjectMapper objectMapper) {
        this.resourceZipService = resourceZipService;
        this.resourceCreationService = resourceCreationService;
        this.importResourceRepository = importResourceRepository;
        this.resourceRepository = resourceRepository;
        this.publishedActaCounter = new PublishedActaCounter(objectMapper);
    }

    /**
     * Stores the extracted content of {@code importManifest} in the import folder and creates or re-opens the
     * ACTAS import resources of its seasons.
     *
     * @return the ACTAS import resources of the manifest's seasons, in manifest season order; empty when the
     *         manifest has no ACTAS asset
     */
    public List<ImportResource> loadIntoRepository(ImportManifest importManifest) {

        Path importFolder = Path.of(resourceZipService.getFolderFromSetting());
        if (!Files.isDirectory(importFolder)) {
            throw new IllegalArgumentException("Configured import folder must exist: " + importFolder);
        }

        List<ImportResource> actasResources = new ArrayList<>();
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
                    Path seasonFolder = resolveSeasonFolder(importFolder, importManifest.source(), assetType, season);
                    List<SeasonFileMove> moves = resolveSeasonFileMoves(importManifest, asset.getValue(), season,
                            resolveDestinationPathToRemoveForAssetType(assetType, season));

                    if (importManifest.mode() == UploadMode.DELTA) {
                        keepRollbackCopy(importFolder, importManifest.source(), assetType, season, seasonFolder);
                        Files.createDirectories(seasonFolder);
                        mergeSeasonContent(moves, seasonFolder);
                    } else {
                        deleteRecursively(seasonFolder);
                        Files.createDirectory(seasonFolder);
                        moveSeasonContent(moves, seasonFolder);
                    }
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unable to store extracted ZIP content", exception);
            }

            if (ACTAS_ASSET.equalsIgnoreCase(assetType)) {
                actasResources.addAll(createResourcesAndStartProcessing(importManifest, assetType, targetFolder));
            }
        }
        return List.copyOf(actasResources);
    }

    /**
     * Verifies that an incoming snapshot does not shrink the published-acta count of any stored
     * ACTAS season folder. Read-only: it never touches the stored folder.
     *
     * @param importManifest the manifest resolved from the uploaded ZIP
     * @param allowPublishedShrink when true, a shrinking upload is accepted with a warning
     * @throws SnapshotShrinkException when at least one season shrinks and no override is given
     */
    public void verifyPublishedActasNotShrinking(ImportManifest importManifest, boolean allowPublishedShrink) {
        List<String> actasFiles = importManifest.assets().entrySet().stream()
                .filter(asset -> ACTAS_ASSET.equalsIgnoreCase(asset.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
        if (actasFiles == null) {
            return;
        }

        Path importFolder = Path.of(resourceZipService.getFolderFromSetting());
        if (!Files.isDirectory(importFolder)) {
            throw new IllegalArgumentException("Configured import folder must exist: " + importFolder);
        }

        List<SnapshotShrinkException.SeasonShrink> shrinks = new ArrayList<>();
        for (String season : importManifest.seasons()) {
            Path targetFolder = resolveSeasonFolder(importFolder, importManifest.source(), ACTAS_ASSET, season);
            List<SeasonFileMove> moves;
            try {
                moves = resolveSeasonFileMoves(importManifest, actasFiles, season,
                        resolveDestinationPathToRemoveForAssetType(ACTAS_ASSET, season));
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to inspect uploaded actas for the shrink check", exception);
            }

            int incoming = importManifest.mode() == UploadMode.DELTA
                    ? projectedMergedPublishedCount(targetFolder, moves)
                    : publishedActaCounter.countPublished(
                            moves.stream().map(SeasonFileMove::source).toList());
            int stored = publishedActaCounter.countPublishedIn(targetFolder);

            if (incoming < stored) {
                shrinks.add(new SnapshotShrinkException.SeasonShrink(
                        importManifest.source(), season, stored, incoming));
            }
        }

        if (shrinks.isEmpty()) {
            return;
        }
        if (allowPublishedShrink) {
            for (SnapshotShrinkException.SeasonShrink shrink : shrinks) {
                LOGGER.log(Level.WARNING,
                        "allowPublishedShrink override applied for {0} {1} ACTAS: uploading {2}"
                                + " published actas over {3} already stored",
                        new Object[]{shrink.source(), shrink.season(), shrink.incoming(), shrink.stored()});
            }
            return;
        }
        throw new SnapshotShrinkException(importManifest.mode(), shrinks);
    }

    /**
     * Projects the published-acta count the stored season folder would hold after a delta merge:
     * published stored files whose relative path is not a move destination, plus the published
     * incoming files. Read-only: it never touches the stored folder.
     */
    private int projectedMergedPublishedCount(Path seasonFolder, List<SeasonFileMove> moves) {
        Set<Path> destinations = moves.stream()
                .map(SeasonFileMove::relativeDestination)
                .collect(Collectors.toSet());
        List<Path> keptStoredFiles = new ArrayList<>();
        if (Files.isDirectory(seasonFolder)) {
            try (var entries = Files.walk(seasonFolder)) {
                for (Path file : entries.filter(Files::isRegularFile).toList()) {
                    if (!destinations.contains(seasonFolder.relativize(file))) {
                        keptStoredFiles.add(file);
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to inspect stored actas for the shrink check", exception);
            }
        }
        int publishedIncoming = publishedActaCounter.countPublished(
                moves.stream().map(SeasonFileMove::source).toList());
        return publishedActaCounter.countPublished(keptStoredFiles) + publishedIncoming;
    }

    private Path resolveSeasonFolder(Path importFolder, String source, String assetType, String season) {
        Path targetFolder = importFolder.resolve(
                String.format(IMPORT_FOLDER_TEMPLATE,
                        source.toLowerCase(Locale.ROOT),
                        assetType.toLowerCase(Locale.ROOT))
        );
        Path seasonFolder = targetFolder.resolve(season).normalize();
        if (!seasonFolder.startsWith(targetFolder)) {
            throw new IllegalArgumentException("Invalid season folder: " + season);
        }
        return seasonFolder;
    }

    private Path resolveRollbackFolder(Path importFolder, String source, String assetType, String season) {
        Path targetFolder = importFolder.resolve(
                String.format(UPLOAD_ROLLBACK_FOLDER_TEMPLATE,
                        source.toLowerCase(Locale.ROOT),
                        assetType.toLowerCase(Locale.ROOT))
        );
        Path rollbackFolder = targetFolder.resolve(season).normalize();
        if (!rollbackFolder.startsWith(targetFolder)) {
            throw new IllegalArgumentException("Invalid season folder: " + season);
        }
        return rollbackFolder;
    }

    private Path resolveDestinationPathToRemoveForAssetType(String assetType, String season) {
        if ("TEAMS".equalsIgnoreCase(assetType)) {
            return Path.of("equipos-json/");
        } else {
            return Path.of("actas-json/" + season);
        }
    }

    private List<SeasonFileMove> resolveSeasonFileMoves(ImportManifest importManifest,
                                                        List<String> files,
                                                        String season,
                                                        Path pathToRemove) throws IOException {
        Path extractionFolder = importManifest.extractionFolder();
        Path extractedSeasonFolder = extractionFolder.resolve(season).normalize();
        if (files.isEmpty() && Files.isDirectory(extractedSeasonFolder)) {
            try (var entries = Files.walk(extractedSeasonFolder)) {
                return entries.filter(Files::isRegularFile)
                        .map(source -> new SeasonFileMove(source,
                                extractedSeasonFolder.relativize(source)))
                        .toList();
            }
        }

        List<SeasonFileMove> moves = new ArrayList<>();
        for (String file : files) {
            Path extractedFile = extractionFolder.resolve(file).normalize();
            if (!extractedFile.startsWith(extractionFolder) || !Files.isRegularFile(extractedFile)) {
                throw new IllegalArgumentException("manifest.json references a missing file: " + file);
            }
            Path relativeFile = extractionFolder.relativize(extractedFile);
            if (relativeFile.startsWith(season)) {
                relativeFile = relativeFile.subpath(1, relativeFile.getNameCount());
            }
            Path relativeDestination = pathToRemove.relativize(relativeFile).normalize();
            moves.add(new SeasonFileMove(extractedFile, relativeDestination));
        }
        return moves;
    }

    private void moveSeasonContent(List<SeasonFileMove> moves, Path seasonFolder) throws IOException {
        for (SeasonFileMove move : moves) {
            Path destination = seasonFolder.resolve(move.relativeDestination()).normalize();
            Files.createDirectories(destination.getParent());
            Files.move(move.source(), destination);
        }
    }

    private void mergeSeasonContent(List<SeasonFileMove> moves, Path seasonFolder) throws IOException {
        for (SeasonFileMove move : moves) {
            Path destination = seasonFolder.resolve(move.relativeDestination()).normalize();
            Files.createDirectories(destination.getParent());
            Files.move(move.source(), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Keeps one rollback copy of the stored season folder for a delta upload, at
     * {@code <import folder>/upload-rollback/<source>/<asset>/<season>/}, replacing the previous copy
     * for the same source, asset and season. The copy lives outside every folder an import reads.
     * With no stored season folder there is nothing to roll back to and no copy is made.
     */
    private void keepRollbackCopy(Path importFolder, String source, String assetType, String season,
                                  Path seasonFolder) throws IOException {
        if (!Files.isDirectory(seasonFolder)) {
            LOGGER.log(Level.INFO, "No stored {0} {1} {2} season folder to roll back",
                    new Object[]{source, assetType, season});
            return;
        }
        Path rollbackFolder = resolveRollbackFolder(importFolder, source, assetType, season);
        deleteRecursively(rollbackFolder);
        Files.createDirectories(rollbackFolder.getParent());
        copyRecursively(seasonFolder, rollbackFolder);
    }

    private void copyRecursively(Path source, Path destination) throws IOException {
        try (var entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                Path target = destination.resolve(source.relativize(entry)).normalize();
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(entry, target);
                }
            }
        }
    }

    private record SeasonFileMove(Path source, Path relativeDestination) {
    }

    private List<ImportResource> createResourcesAndStartProcessing(ImportManifest importManifest,
                                                                   String assetType,
                                                                   Path targetFolder) {
        Resource resolvedResource = createOrGetResource(importManifest, assetType, targetFolder);
        List<ImportResource> importResources = new ArrayList<>();
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
                importResources.add(importResource);
            });
        return importResources;
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
