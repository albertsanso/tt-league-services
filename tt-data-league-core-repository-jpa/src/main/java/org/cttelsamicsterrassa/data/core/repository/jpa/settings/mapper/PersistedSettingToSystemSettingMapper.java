package org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper;
import org.springframework.stereotype.Component;
import org.cttelsamicsterrassa.data.core.domain.settings.model.*;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.PersistedSetting;
import java.util.function.Function;
@Component public class PersistedSettingToSystemSettingMapper implements Function<PersistedSetting,SystemSetting> {
    private final SystemSettingCatalog catalog = new SystemSettingCatalog();
    public SystemSetting apply(PersistedSetting setting) { return setting == null ? null : catalog.rehydrate(setting.getKey(), SettingType.valueOf(setting.getType()), setting.getValue(), setting.getVersion()); }
}
