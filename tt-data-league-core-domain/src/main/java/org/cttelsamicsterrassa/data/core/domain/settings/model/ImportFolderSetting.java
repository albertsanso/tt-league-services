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
    public static final String DEFAULT_VALUE = "c:\\tt-repository";

    private ImportFolderSetting() {
    }

    public static boolean matches(SettingCategory category, String name) {
        return CATEGORY == category && NAME.equals(name);
    }

    /**
     * Validates a candidate value for the {@code IMPORT/repository-folder} setting. The value must be
     * non-blank and syntactically usable as a filesystem path. Filesystem existence is checked only
     * at import execution time, not here.
     */
    public static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Setting IMPORT/repository-folder must not be blank");
        }
        try {
            Path.of(value.trim());
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    "Setting IMPORT/repository-folder must be a valid filesystem path: " + value, exception);
        }
    }
}
