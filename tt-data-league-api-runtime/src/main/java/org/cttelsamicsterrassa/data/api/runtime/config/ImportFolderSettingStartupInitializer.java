package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.settings.service.ImportFolderSettingProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Ensures the {@code IMPORT/repository-folder} administrator setting exists as soon as the API runtime
 * starts. Delegates to {@link ImportFolderSettingProvisioningService}, which is idempotent: repeated
 * launches never overwrite an administrator's configured value. When {@code IMPORT_REPOSITORY_FOLDER_INITIAL}
 * is set it is used as the value of a setting that does not exist yet; it must be an absolute path. Any
 * persistence or initialization failure propagates so the application fails to start rather than running
 * unconfigured.
 */
@Component
public class ImportFolderSettingStartupInitializer implements CommandLineRunner {

    static final String INITIAL_FOLDER_VARIABLE = "IMPORT_REPOSITORY_FOLDER_INITIAL";

    private final ImportFolderSettingProvisioningService provisioningService;
    private final String initialFolder;

    public ImportFolderSettingStartupInitializer(
            ImportFolderSettingProvisioningService provisioningService,
            @Value("${tt.league.import.repository-folder-initial:}") String initialFolder) {
        this.provisioningService = provisioningService;
        this.initialFolder = initialFolder;
    }

    @Override
    public void run(String... args) {
        if (initialFolder == null || initialFolder.isBlank()) {
            provisioningService.ensureDefaultExists();
            return;
        }
        provisioningService.ensureExists(requireAbsolutePath(initialFolder.trim()));
    }

    private static String requireAbsolutePath(String folder) {
        try {
            if (Path.of(folder).isAbsolute()) {
                return folder;
            }
        } catch (InvalidPathException e) {
            throw new IllegalStateException(
                    INITIAL_FOLDER_VARIABLE + " is not a valid path: '" + folder + "'", e);
        }
        throw new IllegalStateException(INITIAL_FOLDER_VARIABLE + " must be an absolute path: '" + folder + "'");
    }
}
