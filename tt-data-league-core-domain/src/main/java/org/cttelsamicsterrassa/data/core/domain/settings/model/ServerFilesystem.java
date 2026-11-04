package org.cttelsamicsterrassa.data.core.domain.settings.model;

import java.util.Locale;

/**
 * Tells the folder settings which path conventions the server applies. Anything other than Windows is treated
 * as POSIX, where a backslash is an ordinary file name character and a drive letter has no meaning.
 */
final class ServerFilesystem {

    static final String OS_NAME = System.getProperty("os.name");
    static final boolean WINDOWS = isWindows(OS_NAME);

    private ServerFilesystem() {
    }

    static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
