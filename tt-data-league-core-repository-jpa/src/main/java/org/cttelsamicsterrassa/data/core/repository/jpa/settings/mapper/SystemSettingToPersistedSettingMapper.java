package org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper;
import org.springframework.stereotype.Component;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.PersistedSetting;
import java.util.function.Function;
@Component public class SystemSettingToPersistedSettingMapper implements Function<SystemSetting,PersistedSetting> {
    public PersistedSetting apply(SystemSetting setting) { return setting == null ? null : new PersistedSetting(setting); }
}
