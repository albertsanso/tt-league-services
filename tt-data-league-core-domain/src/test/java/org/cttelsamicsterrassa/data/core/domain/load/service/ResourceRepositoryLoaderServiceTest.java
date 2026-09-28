package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.repository.ResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.service.ResourceCreationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResourceRepositoryLoaderServiceTest {

    @Test
    void rejectsAMissingConfiguredImportFolder(@TempDir Path workDir) {
        TestDoubles doubles = new TestDoubles();
        Path missingFolder = workDir.resolve("does-not-exist");
        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(missingFolder.toString());

        ResourceRepositoryLoaderService service = doubles.service();
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), workDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.loadIntoRepository(manifest));

        assertTrue(exception.getMessage().contains("Configured import folder must exist"));
        verifyNoInteractions(doubles.resourceCreationService, doubles.importResourceRepository,
                doubles.resourceRepository);
    }

    @Test
    void rejectsANonDirectoryConfiguredImportFolder(@TempDir Path workDir) throws Exception {
        TestDoubles doubles = new TestDoubles();
        Path fileInsteadOfFolder = Files.createFile(workDir.resolve("not-a-folder.txt"));
        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(fileInsteadOfFolder.toString());

        ResourceRepositoryLoaderService service = doubles.service();
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), workDir);

        assertThrows(IllegalArgumentException.class, () -> service.loadIntoRepository(manifest));
        verifyNoInteractions(doubles.resourceCreationService, doubles.importResourceRepository,
                doubles.resourceRepository);
    }

    @Test
    void usesAValidConfiguredImportFolder(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        Files.createDirectory(extractionFolder.resolve("2025-2026"));

        TestDoubles doubles = new TestDoubles();
        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(importFolder.toString());

        ResourceRepositoryLoaderService service = doubles.service();
        ImportManifest manifest = new ImportManifest(
                "RFETM", List.of("2025-2026"), Map.of("TEAMS", List.of()), extractionFolder);

        service.loadIntoRepository(manifest);

        Path seasonFolder = importFolder.resolve("import-rfetm/teams").resolve("2025-2026");
        assertTrue(Files.isDirectory(seasonFolder));
        verifyNoInteractions(doubles.resourceCreationService, doubles.importResourceRepository,
                doubles.resourceRepository);
    }

    @Test
    void movesExplicitActasFilesToTheSameRelativePathsAsBefore(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        Path actasSeason = Files.createDirectories(extractionFolder.resolve("actas-json/2025-2026/jornada-1"));
        Files.writeString(actasSeason.resolve("acta-1.json"), "{\"jornada\": 1}");
        Files.writeString(actasSeason.resolve("acta-2.json"), "{\"jornada\": 2}");

        TestDoubles doubles = new TestDoubles();
        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(importFolder.toString());
        when(doubles.resourceRepository.findByLogicPathAndName(any(), any()))
                .thenReturn(Optional.of(mock(Resource.class)));
        when(doubles.importResourceRepository.findBySourceAndTypeAndSeason(any(), any(), any()))
                .thenReturn(Optional.empty());

        ResourceRepositoryLoaderService service = doubles.service();
        ImportManifest manifest = new ImportManifest("RFETM", List.of("2025-2026"),
                Map.of("ACTAS", List.of("actas-json/2025-2026/jornada-1/acta-1.json",
                        "actas-json/2025-2026/jornada-1/acta-2.json")), extractionFolder);

        service.loadIntoRepository(manifest);

        Path seasonFolder = importFolder.resolve("import-rfetm/actas").resolve("2025-2026");
        assertTrue(Files.isRegularFile(seasonFolder.resolve("jornada-1/acta-1.json")));
        assertTrue(Files.isRegularFile(seasonFolder.resolve("jornada-1/acta-2.json")));
    }

    @Test
    void acceptsAnUploadWhenNoSeasonFolderIsStoredYet(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2025-2026", "a1.json", true);
        writeActa(extractionFolder, "2025-2026", "a2.json", true);

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = actasManifest(extractionFolder, "FCTT", List.of("2025-2026"));

        service.verifyPublishedActasNotShrinking(manifest, false);
    }

    @Test
    void acceptsAnUploadWithTheSamePublishedCountAsStored(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path stored = storedActasSeason(importFolder, "FCTT", "2025-2026", 2);
        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2025-2026", "b1.json", true);
        writeActa(extractionFolder, "2025-2026", "b2.json", true);

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = actasManifest(extractionFolder, "FCTT", List.of("2025-2026"));

        service.verifyPublishedActasNotShrinking(manifest, false);

        assertEquals(2, Files.list(stored).count());
    }

    @Test
    void acceptsAMovingFcttWindowWithAtLeastAsManyPublishedActas(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path stored = storedActasSeason(importFolder, "FCTT", "2026-2027", 3);

        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2026-2027", "j2-new.json", true);
        writeActa(extractionFolder, "2026-2027", "j3-new.json", true);
        writeActa(extractionFolder, "2026-2027", "j4-new.json", true);
        writeActa(extractionFolder, "2026-2027", "j5-pending-a.json", false);
        writeActa(extractionFolder, "2026-2027", "j5-pending-b.json", false);

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = actasManifest(extractionFolder, "FCTT", List.of("2026-2027"));

        service.verifyPublishedActasNotShrinking(manifest, false);
    }

    @Test
    void rejectsAShrinkingSnapshotAndKeepsTheStoredFolderUntouched(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path stored = storedActasSeason(importFolder, "FCTT", "2026-2027", 3);
        byte[][] before = readAll(stored);

        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2026-2027", "a1.json", true);
        writeActa(extractionFolder, "2026-2027", "a2.json", true);
        writeActa(extractionFolder, "2026-2027", "pending-1.json", false);
        writeActa(extractionFolder, "2026-2027", "pending-2.json", false);
        writeActa(extractionFolder, "2026-2027", "pending-3.json", false);

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = actasManifest(extractionFolder, "FCTT", List.of("2026-2027"));

        SnapshotShrinkException exception = assertThrows(SnapshotShrinkException.class,
                () -> service.verifyPublishedActasNotShrinking(manifest, false));

        assertEquals(1, exception.getSeasonShrinks().size());
        SnapshotShrinkException.SeasonShrink shrink = exception.getSeasonShrinks().getFirst();
        assertEquals("FCTT", shrink.source());
        assertEquals("2026-2027", shrink.season());
        assertEquals(3, shrink.stored());
        assertEquals(2, shrink.incoming());
        assertTrue(exception.getMessage().contains("FCTT 2026-2027 ACTAS"));
        assertTrue(exception.getMessage().contains("2 published actas, fewer than the 3 already stored"));
        assertTrue(exception.getMessage().contains("allowPublishedShrink=true"));

        byte[][] after = readAll(stored);
        assertEquals(before.length, after.length);
        for (int i = 0; i < before.length; i++) {
            assertArrayEquals(before[i], after[i]);
        }
    }

    @Test
    void acceptsAShrinkingSnapshotWithTheOverrideAndTheFollowUpLoadReplacesTheFolder(@TempDir Path workDir)
            throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        storedActasSeason(importFolder, "FCTT", "2026-2027", 3);

        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2026-2027", "a1.json", true);
        writeActa(extractionFolder, "2026-2027", "a2.json", true);

        TestDoubles doubles = new TestDoubles();        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(importFolder.toString());
        when(doubles.resourceRepository.findByLogicPathAndName(any(), any()))
                .thenReturn(Optional.of(mock(Resource.class)));
        when(doubles.importResourceRepository.findBySourceAndTypeAndSeason(any(), any(), any()))
                .thenReturn(Optional.empty());
        ResourceRepositoryLoaderService service = doubles.service();
        ImportManifest manifest = actasManifest(extractionFolder, "FCTT", List.of("2026-2027"));

        service.verifyPublishedActasNotShrinking(manifest, true);

        service.loadIntoRepository(manifest);
        Path seasonFolder = importFolder.resolve("import-fctt/actas").resolve("2026-2027");
        assertEquals(2, Files.list(seasonFolder).count());
    }

    @Test
    void rejectsAShorterRfetmSnapshotWhoseFilesHaveNoPublishedField(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        storedActasSeason(importFolder, "RFETM", "2025-2026", 3);

        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2025-2026", "short-1.json", null);
        writeActa(extractionFolder, "2025-2026", "short-2.json", null);

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = actasManifest(extractionFolder, "RFETM", List.of("2025-2026"));

        SnapshotShrinkException exception = assertThrows(SnapshotShrinkException.class,
                () -> service.verifyPublishedActasNotShrinking(manifest, false));
        assertEquals(3, exception.getSeasonShrinks().getFirst().stored());
        assertEquals(2, exception.getSeasonShrinks().getFirst().incoming());
    }

    @Test
    void neverChecksATeamsOnlyManifest(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        Path storedTeams = Files.createDirectories(
                importFolder.resolve("import-rfetm/teams").resolve("2025-2026"));
        Files.writeString(storedTeams.resolve("team-1.json"), "{}");
        Files.writeString(storedTeams.resolve("team-2.json"), "{}");
        Files.writeString(storedTeams.resolve("team-3.json"), "{}");

        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        Files.createDirectories(extractionFolder.resolve("2025-2026"));

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = new ImportManifest("RFETM", List.of("2025-2026"),
                Map.of("TEAMS", List.of()), extractionFolder);

        service.verifyPublishedActasNotShrinking(manifest, false);
    }

    @Test
    void listsOnlyTheShrinkingSeasonOfATwoSeasonManifest(@TempDir Path workDir) throws Exception {
        Path importFolder = Files.createDirectory(workDir.resolve("import-folder"));
        storedActasSeason(importFolder, "FCTT", "2025-2026", 3);
        storedActasSeason(importFolder, "FCTT", "2026-2027", 2);

        Path extractionFolder = Files.createDirectory(workDir.resolve("extracted"));
        writeActa(extractionFolder, "2025-2026", "a1.json", true);
        writeActa(extractionFolder, "2025-2026", "a2.json", true);
        writeActa(extractionFolder, "2026-2027", "b1.json", true);
        writeActa(extractionFolder, "2026-2027", "b2.json", true);

        ResourceRepositoryLoaderService service = serviceWithFolders(importFolder, new TestDoubles());
        ImportManifest manifest = actasManifest(extractionFolder, "FCTT", List.of("2025-2026", "2026-2027"));

        SnapshotShrinkException exception = assertThrows(SnapshotShrinkException.class,
                () -> service.verifyPublishedActasNotShrinking(manifest, false));
        assertEquals(1, exception.getSeasonShrinks().size());
        assertEquals("2025-2026", exception.getSeasonShrinks().getFirst().season());
    }

    @Test
    void requiresTheImportFolderToExistForTheCheck(@TempDir Path workDir) {
        TestDoubles doubles = new TestDoubles();
        Path missingFolder = workDir.resolve("does-not-exist");
        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(missingFolder.toString());

        ResourceRepositoryLoaderService service = doubles.service();
        ImportManifest manifest = actasManifest(workDir, "FCTT", List.of("2025-2026"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.verifyPublishedActasNotShrinking(manifest, false));
        assertTrue(exception.getMessage().contains("Configured import folder must exist"));
    }

    private static ResourceRepositoryLoaderService serviceWithFolders(Path importFolder, TestDoubles doubles) {
        when(doubles.resourceZipService.getFolderFromSetting()).thenReturn(importFolder.toString());
        return doubles.service();
    }

    private static ImportManifest actasManifest(Path extractionFolder, String source, List<String> seasons) {
        return new ImportManifest(source, seasons, Map.of("ACTAS", List.of()), extractionFolder);
    }

    private static void writeActa(Path extractionFolder, String season, String filename, Boolean published)
            throws Exception {
        Path seasonFolder = Files.createDirectories(extractionFolder.resolve(season));
        String content = published == null ? "{\"jornada\": 1}"
                : "{\"acta_publicada\": " + published + "}";
        Files.writeString(seasonFolder.resolve(filename), content);
    }

    private static void writeActaInto(Path folder, String filename, Boolean published) throws Exception {
        String content = published == null ? "{\"jornada\": 1}"
                : "{\"acta_publicada\": " + published + "}";
        Files.writeString(folder.resolve(filename), content);
    }

    private static Path storedActasSeason(Path importFolder, String source, String season, int publishedCount)
            throws Exception {
        Path seasonFolder = Files.createDirectories(
                importFolder.resolve("import-" + source.toLowerCase() + "/actas").resolve(season));
        for (int i = 0; i < publishedCount; i++) {
            writeActaInto(seasonFolder, "stored-" + i + ".json", true);
        }
        return seasonFolder;
    }

    private static byte[][] readAll(Path folder) throws Exception {
        List<byte[]> contents = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.toList()) {
                contents.add(Files.readAllBytes(file));
            }
        }
        return contents.toArray(new byte[0][]);
    }

    private static class TestDoubles {
        final ResourceZipService resourceZipService = mock(ResourceZipService.class);
        final ResourceCreationService resourceCreationService = mock(ResourceCreationService.class);
        final ImportResourceRepository importResourceRepository = mock(ImportResourceRepository.class);
        final ResourceRepository resourceRepository = mock(ResourceRepository.class);

        ResourceRepositoryLoaderService service() {
            return new ResourceRepositoryLoaderService(resourceZipService, resourceCreationService,
                    importResourceRepository, resourceRepository, new ObjectMapper());
        }
    }
}
