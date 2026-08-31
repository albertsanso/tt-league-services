package org.cttelsamicsterrassa.data.core.repository.jpa.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSettingCatalog;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SettingsRepositoryJpaTest {
    @Autowired
    private SettingsRepository repository;

    @Test
    void persistsTypedSettingAndReplacesAtomically() {
        SystemSetting setting = new SystemSettingCatalog().rehydrate("ui.theme", org.cttelsamicsterrassa.data.core.domain.settings.model.SettingType.STRING, "dark", 1);
        repository.save(setting, 0);
        assertThat(repository.findAll()).extracting(SystemSetting::getKey, SystemSetting::getValue, SystemSetting::getVersion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ui.theme", "dark", 1L));

        SystemSetting replacement = new SystemSettingCatalog().rehydrate("ui.theme", org.cttelsamicsterrassa.data.core.domain.settings.model.SettingType.STRING, "system", 2);
        repository.replaceAll(Map.of("ui.theme", replacement));
        assertThat(repository.findAll()).extracting(SystemSetting::getValue).containsExactly("system");
    }
}
