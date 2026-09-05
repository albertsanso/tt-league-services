package org.cttelsamicsterrassa.data.core.domain.load.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class ResourceZipService {


    public static final String IMPORT_FOLDER = "import-folder";

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
            return validateManifest(extractionFolder);
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
                    || !manifest.has("assets")
                    || manifest.size() != 3) {
                throw new IllegalArgumentException("manifest.json must contain only source, seasons, and assets");
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
            return new ImportManifest(sourceValue, seasons, assets, extractionFolder);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid manifest.json", exception);
        }
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
        return settingFinderService.findByCategoryAndName(SettingCategory.IMPORT, IMPORT_FOLDER)
                .orElseThrow(() -> new IllegalArgumentException("Import folder setting is required"))
                .getValue();
    }
}
