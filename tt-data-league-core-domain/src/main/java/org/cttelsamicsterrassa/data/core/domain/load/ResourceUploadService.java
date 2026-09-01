package org.cttelsamicsterrassa.data.core.domain.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.service.ResourceCreationService;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class ResourceUploadService {

    public static final String IMPORT_FOLDER = "import-folder";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SettingFinderService settingFinderService;
    private final ResourceCreationService resourceCreationService;
    private final ImportResourceRepository importResourceRepository;

    @Inject
    public ResourceUploadService(SettingFinderService settingFinderService, ResourceCreationService resourceCreationService, ImportResourceRepository importResourceRepository) {
        this.settingFinderService = settingFinderService;
        this.resourceCreationService = resourceCreationService;
        this.importResourceRepository = importResourceRepository;
    }

    public Path processZip(String filename, byte[] content) {

        validateFile(filename, content);

        // Extract the ZIP file into a temporary directory and validate its root manifest
        ImportManifest importManifest = extractManifestFromZip(content);

        return null;
        /*
        try {
            Path importFolder = Path.of(getFolderFromSetting());
            if (!Files.isDirectory(importFolder)) {
                throw new IllegalArgumentException("Configured import folder must exist: " + importFolder);
            }
            Path extractionFolder = importFolder.resolve("import-" + UUID.randomUUID());
            Files.createDirectory(extractionFolder);
            extractZip(content, extractionFolder);
            ImportManifest importManifest = validateManifest(extractionFolder);





            return extractionFolder;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to extract ZIP file", exception);
        }*/
    }

    private ImportManifest extractManifestFromZip(byte[] content) {
        try {
            Path extractionFolder = Files.createTempDirectory("import-");
            extractZip(content, extractionFolder);
            return validateManifest(extractionFolder);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to extract ZIP file", exception);
        }
    }

    private static void extractZip(byte[] content, Path extractionFolder) throws IOException {
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

    public static ImportManifest validateManifest(Path extractionFolder) {
        Path manifestFile = extractionFolder.resolve("manifest.json");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalArgumentException("ZIP file must contain a root manifest.json file");
        }

        try {
            JsonNode manifest = OBJECT_MAPPER.readTree(manifestFile.toFile());
            if (manifest == null || !manifest.isObject()
                    || !manifest.has("source") || !manifest.has("seasons") || !manifest.has("files")
                    || manifest.size() != 3) {
                throw new IllegalArgumentException("manifest.json must contain only source, seasons, and files");
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
            List<String> files = readTextArray(manifest.get("files"), "files");
            return new ImportManifest(sourceValue, seasons, files, extractionFolder);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid manifest.json", exception);
        }
    }

    private static List<String> readTextArray(JsonNode value, String fieldName) {
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

    public record ImportManifest(String source, List<String> seasons, List<String> files, Path extractionFolder) {
    }

    private static void validateFile(String filename, byte[] content) {
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

    private String getFolderFromSetting() {
        return settingFinderService.findByCategoryAndName(SettingCategory.IMPORT, IMPORT_FOLDER)
                .orElseThrow(() -> new IllegalArgumentException("Import folder setting is required"))
                .getValue();
    }
}
