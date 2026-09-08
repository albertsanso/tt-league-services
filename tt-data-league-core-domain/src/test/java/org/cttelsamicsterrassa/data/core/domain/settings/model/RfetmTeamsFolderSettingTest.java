package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.junit.jupiter.api.Test;

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
        assertEquals("import-rfetm\\teams", RfetmTeamsFolderSetting.DEFAULT_VALUE);
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
        assertThrows(IllegalArgumentException.class, () -> RfetmTeamsFolderSetting.validate("c:\\invalid\u0000path"));
    }

    @Test
    void acceptsSyntacticallyValidPaths() {
        assertDoesNotThrow(() -> RfetmTeamsFolderSetting.validate("import-rfetm\\teams"));
        assertDoesNotThrow(() -> RfetmTeamsFolderSetting.validate("/var/tt-repository/import-rfetm/teams"));
    }
}
