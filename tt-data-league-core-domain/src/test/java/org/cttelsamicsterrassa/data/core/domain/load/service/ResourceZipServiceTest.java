package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ManifestProvenance;
import org.cttelsamicsterrassa.data.core.domain.resource.model.MatchCounts;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void aManifestWithoutProvenanceHasEmptyProvenance(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, """
                {
                  "source": "RFETM",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}}
                }
                """);

        assertEquals(ManifestProvenance.EMPTY, service().validateManifest(extractionFolder).provenance());
    }

    @Test
    void parsesEveryProvenanceField(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, manifestWith("""
                "runId": "0f3c9a_run-1.2",
                "generator": "tt-league-ingest",
                "generatorVersion": "0.1.0",
                "contentSha256": "%s",
                "matchCounts": {"expected": 5, "withResult": 3, "pending": 2}
                """.formatted("a".repeat(64))));

        ManifestProvenance provenance = service().validateManifest(extractionFolder).provenance();

        assertEquals(Optional.of("0f3c9a_run-1.2"), provenance.runId());
        assertEquals(Optional.of("tt-league-ingest"), provenance.generator());
        assertEquals(Optional.of("0.1.0"), provenance.generatorVersion());
        assertEquals(Optional.of("a".repeat(64)), provenance.contentSha256());
        assertEquals(Optional.of(new MatchCounts(5, 3, 2)), provenance.matchCounts());
    }

    @Test
    void parsesAPartialProvenance(@TempDir Path extractionFolder) throws Exception {
        writeManifest(extractionFolder, manifestWith("\"generator\": \"other-tool\""));

        ManifestProvenance provenance = service().validateManifest(extractionFolder).provenance();

        assertEquals(Optional.of("other-tool"), provenance.generator());
        assertTrue(provenance.runId().isEmpty());
        assertTrue(provenance.matchCounts().isEmpty());
    }

    @Test
    void rejectsMalformedProvenanceValues(@TempDir Path extractionFolder) throws Exception {
        List<String> malformed = List.of(
                "\"runId\": \"\"",
                "\"runId\": \"run id\"",
                "\"runId\": \"run/1\"",
                "\"runId\": \"%s\"".formatted("r".repeat(65)),
                "\"runId\": 12",
                "\"runId\": null",
                "\"generator\": \"   \"",
                "\"generator\": \"%s\"".formatted("g".repeat(101)),
                "\"generatorVersion\": \"%s\"".formatted("1".repeat(101)),
                "\"generatorVersion\": null",
                "\"contentSha256\": \"%s\"".formatted("A".repeat(64)),
                "\"contentSha256\": \"%s\"".formatted("a".repeat(63)),
                "\"contentSha256\": \"%s\"".formatted("g".repeat(64)),
                "\"matchCounts\": null",
                "\"matchCounts\": [1, 1, 0]",
                "\"matchCounts\": {\"expected\": 1, \"withResult\": 1}",
                "\"matchCounts\": {\"expected\": 1, \"withResult\": 1, \"pending\": 0, \"extra\": 0}",
                "\"matchCounts\": {\"expected\": 1, \"withResult\": 1, \"unpublished\": 0}",
                "\"matchCounts\": {\"expected\": -1, \"withResult\": 0, \"pending\": -1}",
                "\"matchCounts\": {\"expected\": 1.5, \"withResult\": 1, \"pending\": 0}",
                "\"matchCounts\": {\"expected\": \"1\", \"withResult\": 1, \"pending\": 0}",
                "\"matchCounts\": {\"expected\": 99999999999999999999, \"withResult\": 1, \"pending\": 0}",
                "\"matchCounts\": {\"expected\": 3, \"withResult\": 1, \"pending\": 1}");
        for (String field : malformed) {
            writeManifest(extractionFolder, manifestWith(field));
            assertThrows(IllegalArgumentException.class, () -> service().validateManifest(extractionFolder),
                    () -> "expected a rejection for " + field);
        }
    }

    @Test
    void acceptsAZipWhoseContentHashMatches() throws Exception {
        byte[] content = ContentHashTest.zip(withManifest(ContentHashTest.SHARED_FIXTURE_HASH), 6);

        ImportManifest manifest = service().extractZipAndGetManifest(content);
        try {
            assertEquals(Optional.of(ContentHashTest.SHARED_FIXTURE_HASH), manifest.provenance().contentSha256());
        } finally {
            deleteRecursively(manifest.extractionFolder());
        }
    }

    @Test
    void rejectsAZipWhoseContentHashDoesNotMatch() throws Exception {
        byte[] content = ContentHashTest.zip(withManifest("0".repeat(64)), 6);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service().extractZipAndGetManifest(content));
        assertEquals("manifest.json contentSha256 does not match the ZIP content", exception.getMessage());
    }

    private static Map<String, String> withManifest(String contentSha256) {
        Map<String, String> entries = new LinkedHashMap<>(ContentHashTest.sharedFixture());
        entries.put("manifest.json", """
                {
                  "source": "RFETM",
                  "seasons": ["2026-2027"],
                  "assets": {"ACTAS": {"files": []}, "TEAMS": {"files": ["equipos-json/2026-2027.json"]}},
                  "contentSha256": "%s"
                }
                """.formatted(contentSha256));
        return entries;
    }

    private static String manifestWith(String extraFields) {
        return """
                {
                  "source": "RFETM",
                  "seasons": ["2025-2026"],
                  "assets": {"ACTAS": {"files": ["actas-json/acta.json"]}},
                  %s
                }
                """.formatted(extraFields);
    }

    private static void deleteRecursively(Path folder) throws Exception {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
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
