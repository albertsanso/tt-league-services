package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class StartImportProcessCommandHandler extends DomainCommandHandler<StartImportProcessCommand> {
    private final ImportResourceRepository repository;
    private final ImportResourceProcessService service;

    @Inject
    public StartImportProcessCommandHandler(ImportResourceRepository repository,
                                            ImportResourceProcessService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public DomainCommandResponse handle(StartImportProcessCommand command) {
        return repository.findById(command.getImportResourceId()).map(resource -> {
            if (resource.getStatus() == ImportResourceStatus.PROCESSING) {
                return DomainCommandResponse.failResponse(ImportProcessResultDtoMapper.alreadyProcessing(
                        command.getImportResourceId()));
            }
            resource.setPending();
            resource.startProcessing();
            var result = service.process(resource);
            resource.finishProcessing(result.status() == ImportProcessStatus.SUCCESS);
            repository.save(resource);
            return DomainCommandResponse.successResponse(ImportProcessResultDtoMapper.toDto(resource, result));
        }).orElseGet(() -> DomainCommandResponse.failResponse(
                ImportProcessResultDtoMapper.missingResource(command.getImportResourceId())));
    }
}
