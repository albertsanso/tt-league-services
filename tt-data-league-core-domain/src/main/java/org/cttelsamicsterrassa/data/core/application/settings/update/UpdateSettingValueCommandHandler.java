package org.cttelsamicsterrassa.data.core.application.settings.update;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingModificationService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class UpdateSettingValueCommandHandler extends DomainCommandHandler<UpdateSettingValueCommand> {
    private final SettingModificationService settingModificationService;

    @Inject
    public UpdateSettingValueCommandHandler(SettingModificationService settingModificationService) {
        this.settingModificationService = settingModificationService;
    }

    @Override
    public DomainCommandResponse handle(UpdateSettingValueCommand command) {
        return DomainCommandResponse.successResponse(settingModificationService.modifyValue(
                command.getSettingId(), command.getValue()));
    }
}
