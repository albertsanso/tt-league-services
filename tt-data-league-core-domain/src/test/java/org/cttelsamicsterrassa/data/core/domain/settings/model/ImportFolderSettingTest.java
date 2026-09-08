package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.junit.jupiter.api.Test;

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
        assertEquals("c:\\tt-repository", ImportFolderSetting.DEFAULT_VALUE);
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
        assertThrows(IllegalArgumentException.class, () -> ImportFolderSetting.validate("c:\\invalid\u0000path"));
    }

    @Test
    void acceptsSyntacticallyValidPaths() {
        assertDoesNotThrow(() -> ImportFolderSetting.validate("c:\\tt-repository"));
        assertDoesNotThrow(() -> ImportFolderSetting.validate("/var/tt-repository"));
    }
}
