package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;

import javax.inject.Named;

@Named
public class StartImportProcessCommandHandler extends DomainCommandHandler<StartImportProcessCommand> {
    @Override
    public DomainCommandResponse handle(StartImportProcessCommand command) {
        return null;
    }
}
