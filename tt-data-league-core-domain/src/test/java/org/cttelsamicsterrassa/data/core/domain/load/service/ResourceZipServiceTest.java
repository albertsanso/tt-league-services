package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ResourceZipServiceTest {

    @Test
    void validatesManifestWithMultipleAssetTypes(@TempDir Path extractionFolder) throws Exception {
        Files.writeString(extractionFolder.resolve("manifest.json"), """
                {
                  "source": "RFETM",
                  "seasons": ["2025-2026"],
                  "assets": {
                    "ACTAS": {"files": ["actas-json/acta.json"]},
                    "TEAMS": {"files": ["equipos-json/teams.json"]}
                  }
                }
                """);

        ResourceZipService service = new ResourceZipService(
                mock(SettingFinderService.class), new ObjectMapper());

        ImportManifest manifest = service.validateManifest(extractionFolder);

        assertEquals("RFETM", manifest.source());
        assertEquals(List.of("2025-2026"), manifest.seasons());
        assertEquals(Map.of(
                "ACTAS", List.of("actas-json/acta.json"),
                "TEAMS", List.of("equipos-json/teams.json")), manifest.assets());
        assertEquals(UploadMode.SNAPSHOT, manifest.mode());
    }

    @Test
    void defaultsToSnapshotWhenModeIsAbsent(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);

        ImportManifest manifest = service().validateManifest(extractionFolder);

        assertEquals(UploadMode.SNAPSHOT, manifest.mode());
    }

    @Test
    void parsesTheSnapshotAndDeltaModes(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "mode": "snapshot",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);
        assertEquals(UploadMode.SNAPSHOT, service().validateManifest(extractionFolder).mode());

        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "mode": "delta",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);
        assertEquals(UploadMode.DELTA, service().validateManifest(extractionFolder).mode());
    }

    @Test
    void rejectsAnUppercaseOrUnknownMode(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "mode": "DELTA",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> service().validateManifest(extractionFolder));

        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "mode": "merge",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> service().validateManifest(extractionFolder));
    }

    @Test
    void rejectsANonTextualMode(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "mode": 1,
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> service().validateManifest(extractionFolder));

        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "mode": null,
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> service().validateManifest(extractionFolder));
    }

    @Test
    void rejectsAnUnknownExtraKey(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}},
                  "extra": "not-allowed"
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> service().validateManifest(extractionFolder));
    }

    private static ResourceZipService service() {
        return new ResourceZipService(mock(SettingFinderService.class), new ObjectMapper());
    }

    private static void writeManifest(Path extractionFolder, String content) throws Exception {
        Files.writeString(extractionFolder.resolve("manifest.json"), content);
    }

    @Test
    void rejectsVersionOneManifest(@TempDir Path extractionFolder) throws Exception {
        Files.writeString(extractionFolder.resolve("manifest.json"), """
                {
                  "source": "RFETM",
                  "asset_type": "ACTAS",
                  "seasons": ["2025-2026"],
                  "files": ["acta.json"]
                }
                """);

        ResourceZipService service = new ResourceZipService(
                mock(SettingFinderService.class), new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> service.validateManifest(extractionFolder));
    }
}
