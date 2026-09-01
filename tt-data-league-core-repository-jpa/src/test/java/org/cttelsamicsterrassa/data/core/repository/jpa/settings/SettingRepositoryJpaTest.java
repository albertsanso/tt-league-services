package org.cttelsamicsterrassa.data.core.repository.jpa.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SettingRepositoryJpaTest {

    private static final UUID ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");

    @Autowired
    private SettingRepository settingRepository;

    @Test
    void persistsAndFindsSettings() {
        Setting setting = setting("timezone", "Europe/Madrid");

        settingRepository.save(setting);

        assertEquals(setting.getId(), settingRepository.findById(ID).orElseThrow().getId());
        assertEquals(ID, settingRepository.findByCategoryAndName(SettingCategory.GENERAL, "timezone").orElseThrow().getId());
        assertEquals(1, settingRepository.findAllByCategory(SettingCategory.GENERAL).size());
        assertEquals(1, settingRepository.findAll().size());
    }

    @Test
    void updatesAndDeletesSettings() {
        Setting setting = setting("timezone", "Europe/Madrid");
        settingRepository.save(setting);

        setting.modifyValue("UTC");
        settingRepository.save(setting);

        assertEquals("UTC", settingRepository.findById(ID).orElseThrow().getValue());

        settingRepository.delete(setting);

        assertTrue(settingRepository.findById(ID).isEmpty());
    }

    private static Setting setting(String name, String value) {
        return Setting.createExisting(ID, SettingCategory.GENERAL, name, value);
    }
}
