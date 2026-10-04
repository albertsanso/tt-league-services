package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobAcceptedDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobRejectionDto;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobService;
import org.cttelsamicsterrassa.data.core.domain.load.job.SubmitResult;
import org.cttelsamicsterrassa.data.core.domain.load.service.SnapshotShrinkException;

/**
 * Submits an uploaded ZIP as an import job. Succeeds with an {@link ImportJobAcceptedDto} (new or existing job) and
 * fails with an {@link ImportJobRejectionDto} when the upload is invalid or shrinks published actas.
 *
 * <p>Declared as a bean by the API runtime, like {@link ImportJobService}.</p>
 */
public class SubmitImportJobCommandHandler extends DomainCommandHandler<SubmitImportJobCommand> {

    private final ImportJobService importJobService;

    public SubmitImportJobCommandHandler(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @Override
    public DomainCommandResponse handle(SubmitImportJobCommand command) {
        try {
            SubmitResult result = importJobService.submit(command.getFilename(), command.getContent(),
                    command.getRunId(), command.isAllowPublishedShrink(), command.getRequestedBy());
            return DomainCommandResponse.successResponse(new ImportJobAcceptedDto(result.job().getId(),
                    result.job().getStatus().name(), result.created()));
        } catch (SnapshotShrinkException exception) {
            return DomainCommandResponse.failResponse(
                    new ImportJobRejectionDto(ImportJobRejectionDto.Reason.SHRINK, exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return DomainCommandResponse.failResponse(
                    new ImportJobRejectionDto(ImportJobRejectionDto.Reason.INVALID, exception.getMessage()));
        }
    }
}
