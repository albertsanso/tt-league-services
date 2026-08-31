package org.cttelsamicsterrassa.data.core.application.settings.create;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingCreationService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateSettingCommandHandler extends DomainCommandHandler<CreateSettingCommand> {
    private final SettingCreationService settingCreationService;

    @Inject
    public CreateSettingCommandHandler(SettingCreationService settingCreationService) {
        this.settingCreationService = settingCreationService;
    }

    @Override
    public DomainCommandResponse handle(CreateSettingCommand command) {
        return DomainCommandResponse.successResponse(settingCreationService.create(
                command.getCategory(), command.getName(), command.getValue()));
    }
}
