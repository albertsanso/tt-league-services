package org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.SettingJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class SettingJPAToSettingMapper implements Function<SettingJPA, Setting> {
    @Override
    public Setting apply(SettingJPA settingJPA) {
        return settingJPA == null
                ? null
                : Setting.createExisting(
                        settingJPA.getId(),
                        settingJPA.getCategory(),
                        settingJPA.getName(),
                        settingJPA.getValue());
    }
}
