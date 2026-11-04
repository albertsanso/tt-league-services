package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportFolderSettingTest {

    @Test
    void exposesTheExactCategoryNameAndDefaultValue() {
        assertEquals(SettingCategory.IMPORT, ImportFolderSetting.CATEGORY);
        assertEquals("repository-folder", ImportFolderSetting.NAME);
        assertEquals(ImportFolderSetting.defaultValueFor(System.getProperty("os.name")),
                ImportFolderSetting.DEFAULT_VALUE);
    }

    @Test
    void choosesTheDefaultValueForTheOperatingSystem() {
        assertEquals("c:\\data\\tt-repository", ImportFolderSetting.defaultValueFor("Windows 11"));
        assertEquals("/data/tt-repository", ImportFolderSetting.defaultValueFor("Linux"));
        assertEquals("/data/tt-repository", ImportFolderSetting.defaultValueFor("Mac OS X"));
    }

    @Test
    void theDefaultValueIsValidOnTheRunningOperatingSystem() {
        assertDoesNotThrow(() -> ImportFolderSetting.validate(ImportFolderSetting.DEFAULT_VALUE));
    }

    @Test
    void matchesOnlyTheExactCategoryAndName() {
        assertTrue(ImportFolderSetting.matches(SettingCategory.IMPORT, "repository-folder"));
        assertFalse(ImportFolderSetting.matches(SettingCategory.GENERAL, "repository-folder"));
        assertFalse(ImportFolderSetting.matches(SettingCategory.IMPORT, "other-name"));
    }

    @Test
    void rejectsBlankValues() {
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate(null));
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate(""));
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate("   "));
    }

    @Test
    void rejectsSyntacticallyInvalidPaths() {
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate(Path.of("").toAbsolutePath() + "\u0000path"));
    }

    @Test
    void acceptsAnAbsolutePathOnTheRunningOperatingSystem() {
        assertDoesNotThrow(() -> ImportFolderSetting.validate(Path.of("").toAbsolutePath().toString()));
        assertDoesNotThrow(() -> ImportFolderSetting.validate("  " + Path.of("").toAbsolutePath() + "  "));
    }

    @Test
    void rejectsARelativePath() {
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate("tt-repository"));
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate("data/tt-repository"));
    }

    @Test
    void rejectsAPathWrittenForTheOtherOperatingSystem() {
        String foreign = ServerFilesystem.WINDOWS ? ImportFolderSetting.LINUX_DEFAULT_VALUE
                : ImportFolderSetting.WINDOWS_DEFAULT_VALUE;

        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate(foreign));
    }
}
