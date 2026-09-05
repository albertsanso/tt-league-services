package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
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
