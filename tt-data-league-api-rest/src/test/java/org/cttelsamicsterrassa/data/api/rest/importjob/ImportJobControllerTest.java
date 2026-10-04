package org.cttelsamicsterrassa.data.api.rest.importjob;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.importjob.FindImportJobHistoryQuery;
import org.cttelsamicsterrassa.data.core.application.importjob.FindImportJobQuery;
import org.cttelsamicsterrassa.data.core.application.importjob.SubmitImportJobCommand;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobAcceptedDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobRejectionDto;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobControllerTest {

    private static final Authentication ADMIN = new TestingAuthenticationToken("albert", null, "ROLE_ADMIN");
    private static final MockMultipartFile ZIP =
            new MockMultipartFile("file", "upload.zip", "application/zip", new byte[]{1, 2});

    private final QueryBus queryBus = mock(QueryBus.class);
    private final CommandBus commandBus = mock(CommandBus.class);
    private final ImportJobController controller = controller();

    @Test
    void aNewJobIsAcceptedWith202() {
        ImportJobAcceptedDto accepted = new ImportJobAcceptedDto(UUID.randomUUID(), "QUEUED", true);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse(accepted));

        var response = controller.submit(ZIP, "orch-1", true, ADMIN);

        assertEquals(202, response.getStatusCode().value());
        assertEquals(accepted, response.getBody());
        verify(commandBus).push(argThat(command -> command instanceof SubmitImportJobCommand submit
                && "upload.zip".equals(submit.getFilename())
                && submit.getRunId().equals(Optional.of("orch-1"))
                && submit.isAllowPublishedShrink()
                && "albert".equals(submit.getRequestedBy())
                && submit.getContent().length == 2));
    }

    @Test
    void anExistingJobForTheSameContentIsReturnedWith200() {
        ImportJobAcceptedDto existing = new ImportJobAcceptedDto(UUID.randomUUID(), "SUCCEEDED", false);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse(existing));

        var response = controller.submit(ZIP, null, false, ADMIN);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(existing, response.getBody());
        verify(commandBus).push(argThat(command -> command instanceof SubmitImportJobCommand submit
                && submit.getRunId().isEmpty()));
    }

    @Test
    void anInvalidUploadIsRejectedWith400AndAShrinkWith409() {
        when(commandBus.push(any()))
                .thenReturn(DomainCommandResponse.failResponse(new ImportJobRejectionDto(
                        ImportJobRejectionDto.Reason.INVALID, "manifest.json contentSha256 does not match")))
                .thenReturn(DomainCommandResponse.failResponse(new ImportJobRejectionDto(
                        ImportJobRejectionDto.Reason.SHRINK, "Snapshot shrinks FCTT 2026-2027")));

        var invalid = controller.submit(ZIP, null, false, ADMIN);
        var shrink = controller.submit(ZIP, null, false, ADMIN);

        assertEquals(400, invalid.getStatusCode().value());
        assertEquals(Map.of("message", "manifest.json contentSha256 does not match"), invalid.getBody());
        assertEquals(409, shrink.getStatusCode().value());
        assertEquals(Map.of("message", "Snapshot shrinks FCTT 2026-2027"), shrink.getBody());
    }

    @Test
    void anEmptyFileIsRejectedWithoutSubmitting() {
        var response = controller.submit(new MockMultipartFile("file", "upload.zip", "application/zip",
                new byte[0]), null, false, ADMIN);

        assertEquals(400, response.getStatusCode().value());
        verify(commandBus, never()).push(any());
    }

    @Test
    void aJobIsReadByIdOr404() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(queryBus.push(argThat(query -> query instanceof FindImportJobQuery find
                && known.equals(find.getImportJobId())))).thenReturn(DomainQueryResponse.sucessResponse("job"));
        when(queryBus.push(argThat(query -> query instanceof FindImportJobQuery find
                && unknown.equals(find.getImportJobId())))).thenReturn(DomainQueryResponse.failResponse(null));

        var found = controller.find(known);
        var missing = controller.find(unknown);

        assertEquals(200, found.getStatusCode().value());
        assertEquals("job", found.getBody());
        assertEquals(404, missing.getStatusCode().value());
        assertEquals(Map.of("message", "Import job not found: " + unknown), missing.getBody());
    }

    @Test
    void theHistoryPassesItsFiltersAndReturnsTheList() {
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of()));

        var response = controller.list("FCTT", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4), 10);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(), response.getBody());
        verify(queryBus).push(argThat(query -> query instanceof FindImportJobHistoryQuery history
                && history.getSource().equals(Optional.of(ImportSource.FCTT))
                && history.getFrom().equals(Optional.of(LocalDate.of(2026, 10, 1)))
                && history.getTo().equals(Optional.of(LocalDate.of(2026, 10, 4)))
                && history.getLimit() == 10));
    }

    @Test
    void invalidHistoryFiltersAreRejectedWith400() {
        assertEquals(400, controller.list("ITTF", null, null, null).getStatusCode().value());
        assertEquals(400, controller.list(null, null, null, 500).getStatusCode().value());
        assertEquals(400, controller.list(null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 4), null)
                .getStatusCode().value());
        verify(queryBus, never()).push(any());
    }

    private ImportJobController controller() {
        ImportJobController importJobController = new ImportJobController();
        ReflectionTestUtils.setField(importJobController, "queryBus", queryBus);
        ReflectionTestUtils.setField(importJobController, "commandBus", commandBus);
        return importJobController;
    }
}
