package org.cttelsamicsterrassa.data.core.application.settings.delete;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingDeletionService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DeleteSettingCommandHandler extends DomainCommandHandler<DeleteSettingCommand> {
    private final SettingDeletionService settingDeletionService;

    @Inject
    public DeleteSettingCommandHandler(SettingDeletionService settingDeletionService) {
        this.settingDeletionService = settingDeletionService;
    }

    @Override
    public DomainCommandResponse handle(DeleteSettingCommand command) {
        settingDeletionService.delete(command.getSettingId());
        return DomainCommandResponse.successResponse(command.getSettingId());
    }
}
