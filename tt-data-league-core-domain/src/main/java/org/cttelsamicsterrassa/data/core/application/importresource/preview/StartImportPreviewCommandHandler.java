package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceRepositoryLoaderService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class StartImportPreviewCommandHandler extends DomainCommandHandler<StartImportPreviewCommand> {

    private final ResourceRepositoryLoaderService resourceRepositoryLoaderService;

    @Inject
    public StartImportPreviewCommandHandler(ResourceRepositoryLoaderService resourceRepositoryLoaderService) {
        this.resourceRepositoryLoaderService = resourceRepositoryLoaderService;
    }

    @Override
    public DomainCommandResponse handle(StartImportPreviewCommand startImportPreviewCommand) {

        return null;
    }
}
