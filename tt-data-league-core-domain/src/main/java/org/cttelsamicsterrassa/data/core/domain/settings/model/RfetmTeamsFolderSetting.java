package org.cttelsamicsterrassa.data.core.domain.settings.model;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Canonical contract for the persisted "RFETM teams folder" administrator setting.
 * Startup provisioning, import lookup, and settings validation share this definition so
 * the category, key, and default value cannot drift between call sites.
 */
public final class RfetmTeamsFolderSetting {

    public static final SettingCategory CATEGORY = SettingCategory.IMPORT;
    public static final String NAME = "rfetm-teams-folder";
    public static final String DEFAULT_VALUE = "import-rfetm/teams";

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private RfetmTeamsFolderSetting() {
    }

    public static boolean matches(SettingCategory category, String name) {
        return CATEGORY == category && NAME.equals(name);
    }

    /**
     * Converts a stored value to a path on the server's operating system. Values may use either separator, so
     * one written with backslashes, as the previous default was, still means nested folders on Linux.
     */
    public static Path toPath(String value) {
        return Path.of(normalizeSeparators(value, ServerFilesystem.WINDOWS));
    }

    static String normalizeSeparators(String value, boolean windows) {
        return windows ? value : value.replace('\\', '/');
    }

    /**
     * Validates a candidate value for the {@code IMPORT/rfetm-teams-folder} setting. The value must be
     * non-blank and usable as a filesystem path on the server's operating system. Filesystem existence is
     * checked only at import execution time, not here.
     */
    public static void validate(String value) {
        validate(value, ServerFilesystem.WINDOWS);
    }

    static void validate(String value, boolean windows) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Setting IMPORT/rfetm-teams-folder must not be blank");
        }
        if (!windows && WINDOWS_DRIVE.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Setting IMPORT/rfetm-teams-folder must not use a Windows drive on this server: " + value);
        }
        try {
            Path.of(normalizeSeparators(value.trim(), windows));
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    "Setting IMPORT/rfetm-teams-folder must be a valid filesystem path: " + value, exception);
        }
    }
}
