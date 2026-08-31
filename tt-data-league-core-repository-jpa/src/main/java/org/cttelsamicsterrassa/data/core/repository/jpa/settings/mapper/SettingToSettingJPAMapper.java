package org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.SettingJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class SettingToSettingJPAMapper implements Function<Setting, SettingJPA> {
    @Override
    public SettingJPA apply(Setting setting) {
        return setting == null
                ? null
                : new SettingJPA(
                        setting.getId(),
                        setting.getSettingCategory(),
                        setting.getName(),
                        setting.getValue());
    }
}
