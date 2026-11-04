package org.cttelsamicsterrassa.data.core.domain.settings.model;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Canonical contract for the persisted "default repository folder" administrator setting.
 * Startup provisioning, import lookup, and settings validation share this definition so
 * the category, key, and default value cannot drift between call sites.
 */
public final class ImportFolderSetting {

    public static final SettingCategory CATEGORY = SettingCategory.IMPORT;
    public static final String NAME = "repository-folder";
    public static final String WINDOWS_DEFAULT_VALUE = "c:\\data\\tt-repository";
    public static final String LINUX_DEFAULT_VALUE = "/data/tt-repository";
    public static final String DEFAULT_VALUE = defaultValueFor(ServerFilesystem.OS_NAME);

    private ImportFolderSetting() {
    }

    public static boolean matches(SettingCategory category, String name) {
        return CATEGORY == category && NAME.equals(name);
    }

    static String defaultValueFor(String osName) {
        return ServerFilesystem.isWindows(osName) ? WINDOWS_DEFAULT_VALUE : LINUX_DEFAULT_VALUE;
    }

    /**
     * Validates a candidate value for the {@code IMPORT/repository-folder} setting. The value must be
     * non-blank and an absolute path on the server's operating system, which also rejects a path written for
     * another one, such as {@code c:\repository} on Linux. Filesystem existence is checked only at import
     * execution time, not here.
     */
    public static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Setting IMPORT/repository-folder must not be blank");
        }
        boolean absolute;
        try {
            absolute = Path.of(value.trim()).isAbsolute();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    "Setting IMPORT/repository-folder must be a valid filesystem path: " + value, exception);
        }
        if (!absolute) {
            throw new IllegalArgumentException(
                    "Setting IMPORT/repository-folder must be an absolute path on this server: " + value);
        }
    }
}
