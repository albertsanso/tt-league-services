package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfetmTeamsFolderSettingTest {

    @Test
    void exposesTheExactCategoryNameAndDefaultValue() {
        assertEquals(SettingCategory.IMPORT, RfetmTeamsFolderSetting.CATEGORY);
        assertEquals("rfetm-teams-folder", RfetmTeamsFolderSetting.NAME);
        assertEquals("import-rfetm/teams", RfetmTeamsFolderSetting.DEFAULT_VALUE);
    }

    @Test
    void matchesOnlyTheExactCategoryAndName() {
        assertTrue(RfetmTeamsFolderSetting.matches(SettingCategory.IMPORT, "rfetm-teams-folder"));
        assertFalse(RfetmTeamsFolderSetting.matches(SettingCategory.GENERAL, "rfetm-teams-folder"));
        assertFalse(RfetmTeamsFolderSetting.matches(SettingCategory.IMPORT, "other-name"));
    }

    @Test
    void rejectsBlankValues() {
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate(null));
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate(""));
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate("   "));
    }

    @Test
    void rejectsSyntacticallyInvalidPaths() {
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate("import-rfetm/invalid\u0000path"));
    }

    @Test
    void theDefaultValueIsValidOnTheRunningOperatingSystem() {
        assertDoesNotThrow(() -> RfetmTeamsFolderSetting.validate(RfetmTeamsFolderSetting.DEFAULT_VALUE));
    }

    @Test
    void convertsBackslashesToSlashesOnPosixOnly() {
        assertEquals("import-rfetm/teams", RfetmTeamsFolderSetting.normalizeSeparators("import-rfetm\\teams", false));
        assertEquals("import-rfetm\\teams", RfetmTeamsFolderSetting.normalizeSeparators("import-rfetm\\teams", true));
        assertEquals("import-rfetm/teams", RfetmTeamsFolderSetting.normalizeSeparators("import-rfetm/teams", false));
    }

    @Test
    void aLegacyBackslashValueMeansNestedFoldersOnPosix() {
        assertEquals(2, Path.of(RfetmTeamsFolderSetting.normalizeSeparators("import-rfetm\\teams", false))
                .getNameCount());
    }

    @Test
    void toPathMeansNestedFoldersWhicheverSeparatorIsStored() {
        assertEquals(2, RfetmTeamsFolderSetting.toPath("import-rfetm\\teams").getNameCount());
        assertEquals(2, RfetmTeamsFolderSetting.toPath("import-rfetm/teams").getNameCount());
    }

    @Test
    void rejectsADriveLetterOnPosixButNotOnWindows() {
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate("c:\\import-rfetm", false));
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate("D:/import-rfetm", false));
        if (ServerFilesystem.WINDOWS) {
            assertDoesNotThrow(() -> RfetmTeamsFolderSetting.validate("c:\\import-rfetm", true));
        }
    }

    @Test
    void acceptsSyntacticallyValidPaths() {
        assertDoesNotThrow(() -> RfetmTeamsFolderSetting.validate("import-rfetm\\teams"));
        assertDoesNotThrow(() -> RfetmTeamsFolderSetting.validate("/var/tt-repository/import-rfetm/teams"));
    }
}
