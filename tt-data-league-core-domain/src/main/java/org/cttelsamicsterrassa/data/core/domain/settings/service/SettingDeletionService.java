package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class SettingDeletionService {
    private final SettingRepository settingRepository;

    @Inject
    public SettingDeletionService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public void delete(UUID id) {
        settingRepository.findById(id).ifPresent(settingRepository::delete);
    }
}
