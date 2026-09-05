package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.service.ResourceCreationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResourceRepositoryLoaderServiceTest {

    @Test
    void rejectsAMissingConfiguredImportFolder(@TempDir Path workDir) {
        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceCreationService resourceCreationService = mock(ResourceCreationService.class);
        ImportResourceRepository importResourceRepository = mock(ImportResourceRepository.class);
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        Path missingFolder = workDir.resolve("does-not-exist");
        when(resourceZipService.getFolderFromSetting()).thenReturn(missingFolder.toString());

        ResourceRepositoryLoaderService service = new ResourceRepositoryLoaderService(
                resourceZipService, resourceCreationService, importResourceRepository, resourceRepository);
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), workDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.loadIntoRepository(manifest));

        assertTrue(exception.getMessage().contains("Configured import folder must exist"));
        verifyNoInteractions(resourceCreationService, importResourceRepository, resourceRepository);
    }

    @Test
    void rejectsANonDirectoryConfiguredImportFolder(@TempDir Path workDir) throws Exception {
        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceCreationService resourceCreationService = mock(ResourceCreationService.class);
        ImportResourceRepository importResourceRepository = mock(ImportResourceRepository.class);
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        Path fileInsteadOfFolder = Files.createFile(workDir.resolve("not-a-folder.txt"));
        when(resourceZipService.getFolderFromSetting()).thenReturn(fileInsteadOfFolder.toString());

        ResourceRepositoryLoaderService service = new ResourceRepositoryLoaderService(
                resourceZipService, resourceCreationService, importResourceRepository, resourceRepository);
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), workDir);

        assertThrows(IllegalArgumentException.class, () -> service.loadIntoRepository(manifest));
        verifyNoInteractions(resourceCreationService, importResourceRepository, resourceRepository);
    }

    @Test
    void usesAValidConfiguredImportFolder(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        Files.createDirectory(extractionFolder.resolve("2025-2026"));

        ResourceZipService resourceZipService = mock(ResourceZipService.class);
        ResourceCreationService resourceCreationService = mock(ResourceCreationService.class);
        ImportResourceRepository importResourceRepository = mock(ImportResourceRepository.class);
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        when(resourceZipService.getFolderFromSetting()).thenReturn(importFolder.toString());

        ResourceRepositoryLoaderService service = new ResourceRepositoryLoaderService(
                resourceZipService, resourceCreationService, importResourceRepository, resourceRepository);
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), extractionFolder);

        service.loadIntoRepository(manifest);

        Path seasonFolder = importFolder.resolve("import-rfetm/teams").resolve("2025-2026");
        assertTrue(Files.isDirectory(seasonFolder));
        verifyNoInteractions(resourceCreationService, importResourceRepository, resourceRepository);
    }
}
