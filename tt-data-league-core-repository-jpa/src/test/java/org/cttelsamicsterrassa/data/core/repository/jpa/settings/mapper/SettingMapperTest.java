package org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.SettingJPA;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettingMapperTest {

    private static final UUID ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");

    @Test
    void mapsDomainSettingToJpaSetting() {
        Setting setting = Setting.createExisting(ID, SettingCategory.GENERAL, "timezone", "Europe/Madrid");

        SettingJPA settingJPA = new SettingToSettingJPAMapper().apply(setting);

        assertEquals(ID, settingJPA.getId());
        assertEquals(SettingCategory.GENERAL, settingJPA.getCategory());
        assertEquals("timezone", settingJPA.getName());
        assertEquals("Europe/Madrid", settingJPA.getValue());
    }

    @Test
    void mapsJpaSettingToDomainSetting() {
        SettingJPA settingJPA = new SettingJPA(ID, SettingCategory.GENERAL, "timezone", "Europe/Madrid");

        Setting setting = new SettingJPAToSettingMapper().apply(settingJPA);

        assertEquals(ID, setting.getId());
        assertEquals(SettingCategory.GENERAL, setting.getSettingCategory());
        assertEquals("timezone", setting.getName());
        assertEquals("Europe/Madrid", setting.getValue());
    }

    @Test
    void mapsNullToNull() {
        assertNull(new SettingToSettingJPAMapper().apply(null));
        assertNull(new SettingJPAToSettingMapper().apply(null));
    }
}
