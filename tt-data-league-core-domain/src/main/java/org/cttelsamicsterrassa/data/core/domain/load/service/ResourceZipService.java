package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ManifestProvenance;
import org.cttelsamicsterrassa.data.core.domain.resource.model.MatchCounts;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class ResourceZipService {


    public static final String IMPORT_FOLDER = ImportFolderSetting.NAME;

    private static final String MANIFEST_SHAPE_MESSAGE = "manifest.json must contain source, seasons, assets and "
            + "optionally mode, runId, generator, generatorVersion, contentSha256, matchCounts";
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "source", "seasons", "assets", "mode",
            "runId", "generator", "generatorVersion", "contentSha256", "matchCounts");
    private static final Set<String> MATCH_COUNTS_FIELDS = Set.of("expected", "withResult", "pending");
    private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_GENERATOR_LENGTH = 100;

    private final SettingFinderService settingFinderService;
    private final ObjectMapper objectMapper;

    @Inject
    public ResourceZipService(SettingFinderService settingFinderService, ObjectMapper objectMapper) {
        this.settingFinderService = settingFinderService;
        this.objectMapper = objectMapper;
    }

    public ImportManifest extractZipAndGetManifest(byte[] content) {
        try {
            Path extractionFolder = Files.createTempDirectory("import-");
            extractZip(content, extractionFolder);
            ImportManifest manifest = validateManifest(extractionFolder);
            Optional<String> declaredHash = manifest.provenance().contentSha256();
            if (declaredHash.isPresent() && !declaredHash.get().equals(ContentHash.compute(content))) {
                throw new IllegalArgumentException("manifest.json contentSha256 does not match the ZIP content");
            }
            return manifest;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to extract ZIP file", exception);
        }
    }

    public static void extractZip(byte[] content, Path extractionFolder) throws IOException {
        boolean hasEntries = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                hasEntries = true;
                Path target = extractionFolder.resolve(entry.getName()).normalize();
                if (!target.startsWith(extractionFolder)) {
                    throw new IllegalArgumentException("ZIP entry is outside the extraction folder: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zipInputStream, target);
                }
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
            if (!hasEntries) {
                throw new IllegalArgumentException("ZIP file must contain at least one entry");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid ZIP file", exception);
        }
    }

    public ImportManifest validateManifest(Path extractionFolder) {
        Path manifestFile = extractionFolder.resolve("manifest.json");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalArgumentException("ZIP file must contain a root manifest.json file");
        }

        try {
            JsonNode manifest = objectMapper.readTree(manifestFile.toFile());
            if (manifest == null || !manifest.isObject()
                    || !manifest.has("source")
                    || !manifest.has("seasons")
                    || !manifest.has("assets")) {
                throw new IllegalArgumentException(MANIFEST_SHAPE_MESSAGE);
            }
            var manifestFields = manifest.fieldNames();
            while (manifestFields.hasNext()) {
                if (!ALLOWED_FIELDS.contains(manifestFields.next())) {
                    throw new IllegalArgumentException(MANIFEST_SHAPE_MESSAGE);
                }
            }
            JsonNode source = manifest.get("source");
            if (!source.isTextual()) {
                throw new IllegalArgumentException("manifest.json source must be a valid ImportSource");
            }
            String sourceValue = source.textValue();

            try {
                ImportSource.valueOf(sourceValue);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "manifest.json source must be one of " + List.of(ImportSource.values()), exception);
            }
            List<String> seasons = readTextArray(manifest.get("seasons"), "seasons");
            if (seasons.isEmpty()) {
                throw new IllegalArgumentException("manifest.json seasons must not be empty");
            }
            seasons.forEach(season -> {
                try {
                    Season.fromFormatted(season);
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException("manifest.json contains an invalid season: " + season, exception);
                }
            });
            JsonNode assetsNode = manifest.get("assets");
            if (!assetsNode.isObject() || assetsNode.isEmpty()) {
                throw new IllegalArgumentException("manifest.json assets must be a non-empty object");
            }
            Map<String, List<String>> assets = new LinkedHashMap<>();
            var assetFields = assetsNode.fields();
            while (assetFields.hasNext()) {
                var asset = assetFields.next();
                if (asset.getKey().isBlank()
                        || !asset.getValue().isObject()
                        || asset.getValue().size() != 1
                        || !asset.getValue().has("files")) {
                    throw new IllegalArgumentException(
                            "manifest.json assets must contain asset objects with only a files array");
                }
                assets.put(asset.getKey(), readTextArray(
                        asset.getValue().get("files"), "assets." + asset.getKey() + ".files"));
            }
            UploadMode mode = UploadMode.SNAPSHOT;
            if (manifest.has("mode")) {
                JsonNode modeNode = manifest.get("mode");
                if (!modeNode.isTextual()) {
                    throw new IllegalArgumentException("manifest.json mode must be one of snapshot, delta");
                }
                mode = UploadMode.fromManifestValue(modeNode.textValue());
            }
            return new ImportManifest(sourceValue, seasons, assets, extractionFolder, mode, readProvenance(manifest));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid manifest.json", exception);
        }
    }

    private static ManifestProvenance readProvenance(JsonNode manifest) {
        return new ManifestProvenance(
                readOptionalText(manifest, "runId").map(value -> requireMatch(value, RUN_ID, "runId",
                        "1 to 64 characters among A-Z, a-z, 0-9, '.', '_' and '-'")),
                readOptionalText(manifest, "generator").map(value -> requireGeneratorText(value, "generator")),
                readOptionalText(manifest, "generatorVersion")
                        .map(value -> requireGeneratorText(value, "generatorVersion")),
                readOptionalText(manifest, "contentSha256").map(value -> requireMatch(value, SHA_256,
                        "contentSha256", "64 lowercase hexadecimal characters")),
                manifest.has("matchCounts")
                        ? Optional.of(readMatchCounts(manifest.get("matchCounts")))
                        : Optional.empty());
    }

    private static Optional<String> readOptionalText(JsonNode manifest, String fieldName) {
        if (!manifest.has(fieldName)) {
            return Optional.empty();
        }
        JsonNode value = manifest.get(fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("manifest.json " + fieldName + " must be a string");
        }
        return Optional.of(value.textValue());
    }

    private static String requireMatch(String value, Pattern pattern, String fieldName, String expectation) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("manifest.json " + fieldName + " must be " + expectation);
        }
        return value;
    }

    private static String requireGeneratorText(String value, String fieldName) {
        if (value.isBlank() || value.length() > MAX_GENERATOR_LENGTH) {
            throw new IllegalArgumentException("manifest.json " + fieldName
                    + " must be a non-blank string of at most " + MAX_GENERATOR_LENGTH + " characters");
        }
        return value;
    }

    private static MatchCounts readMatchCounts(JsonNode value) {
        if (!value.isObject() || value.size() != MATCH_COUNTS_FIELDS.size()) {
            throw new IllegalArgumentException(
                    "manifest.json matchCounts must be an object with only expected, withResult and pending");
        }
        var fields = value.fieldNames();
        while (fields.hasNext()) {
            if (!MATCH_COUNTS_FIELDS.contains(fields.next())) {
                throw new IllegalArgumentException(
                        "manifest.json matchCounts must be an object with only expected, withResult and pending");
            }
        }
        return new MatchCounts(
                readCount(value.get("expected"), "expected"),
                readCount(value.get("withResult"), "withResult"),
                readCount(value.get("pending"), "pending"));
    }

    private static long readCount(JsonNode value, String fieldName) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("manifest.json matchCounts." + fieldName + " must be an integer");
        }
        return value.longValue();
    }

    public static List<String> readTextArray(JsonNode value, String fieldName) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("manifest.json " + fieldName + " must be an array of strings");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("manifest.json " + fieldName + " must be an array of strings");
            }
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }

    public static void validateFile(String filename, byte[] content) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("ZIP filename is required");
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP files are supported");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("ZIP file must not be empty");
        }
    }

    public String getFolderFromSetting() {
        return settingFinderService.findByCategoryAndName(ImportFolderSetting.CATEGORY, ImportFolderSetting.NAME)
                .orElseThrow(() -> new IllegalArgumentException("Import folder setting is required"))
                .getValue();
    }
}
