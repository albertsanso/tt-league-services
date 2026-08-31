package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemSettingsServiceTest {
    private final MemorySettingsRepository repository = new MemorySettingsRepository();
    private final SystemSettingsService service = new SystemSettingsService(repository);

    @Test
    void exposesDefaultsAndFiltersByCategoryAndSearch() {
        assertThat(service.list(null, null)).hasSize(10);
        assertThat(service.list(null, "theme")).extracting("key").containsExactly("ui.theme");
        assertThat(service.list(org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory.IMPORT, null))
                .extracting("category").containsOnly(org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory.IMPORT);
    }

    @Test
    void previewDoesNotPersistAndUpdateUsesVersion() {
        service.preview(Map.of("display.maxPageSize", 80));
        assertThat(service.list(null, "maxPageSize").get(0).getValue()).isEqualTo(50);
        assertThat(service.update("display.maxPageSize", 80, 0).getValue()).isEqualTo(80);
        assertThatThrownBy(() -> service.update("display.maxPageSize", 90, 0))
                .isInstanceOf(SettingConflictException.class);
    }

    @Test
    void rejectsUnknownAndUnsafeValues() {
        assertThatThrownBy(() -> service.update("jwt.secret", "secret", 0))
                .isInstanceOf(SettingNotFoundException.class);
        assertThatThrownBy(() -> service.preview(Map.of("display.maxPageSize", 1000)))
                .isInstanceOf(SettingValidationException.class);
    }

    private static final class MemorySettingsRepository implements SettingsRepository {
        private final List<SystemSetting> values = new ArrayList<>();

        @Override
        public List<SystemSetting> findAll() {
            return List.copyOf(values);
        }

        @Override
        public void save(SystemSetting setting, long expectedVersion) {
            values.removeIf(value -> value.getKey().equals(setting.getKey()));
            values.add(setting);
        }

        @Override
        public void replaceAll(Map<String, SystemSetting> settings) {
            values.clear();
            values.addAll(settings.values());
        }
    }
}
