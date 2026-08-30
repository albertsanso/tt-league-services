package org.cttelsamicsterrassa.data.core.repository.jpa.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.model.PersistedSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingType;
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
        PersistedSetting setting = new PersistedSetting("ui.theme", SettingType.STRING, "dark", 1);
        repository.save(setting, 0);
        assertThat(repository.findAll()).containsExactly(setting);

        repository.replaceAll(Map.of("ui.theme", new PersistedSetting("ui.theme", SettingType.STRING, "system", 2)));
        assertThat(repository.findAll()).containsExactly(new PersistedSetting("ui.theme", SettingType.STRING, "system", 2));
    }
}
