package org.cttelsamicsterrassa.data.api.rest.importresource;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.importresource.find.FindPendingImportsInfoQuery;
import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.PendingImportsInfoDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.FindImportPreviewStatusQuery;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.StartImportPreviewCommand;
import org.cttelsamicsterrassa.data.core.application.importresource.process.FindImportRunStatusQuery;
import org.cttelsamicsterrassa.data.core.application.importresource.process.StartImportProcessCommand;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceUploadService;
import org.cttelsamicsterrassa.data.core.domain.load.service.SnapshotShrinkException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportResourceControllerTest {

    @Test
    void statusWithoutResourceIdKeepsTheSourceLevelContract() {
        QueryBus queryBus = mock(QueryBus.class);
        ImportResourceController controller = controller(queryBus, mock(CommandBus.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(new PendingImportsInfoDto(List.of())));

        var response = controller.listSourcesWithPendingImports();

        assertEquals(200, response.getStatusCode().value());
        verify(queryBus).push(argThat(query -> query instanceof FindPendingImportsInfoQuery));
    }

    @Test
    void previewStatusUsesThePreviewStatusQuery() {
        QueryBus queryBus = mock(QueryBus.class);
        UUID importResourceId = UUID.randomUUID();
        ImportResourceController controller = controller(queryBus, mock(CommandBus.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse("ok"));

        var response = controller.findImportPreviewStatus(importResourceId);

        assertEquals(200, response.getStatusCode().value());
        verify(queryBus).push(argThat(query -> query instanceof FindImportPreviewStatusQuery previewQuery
                && importResourceId.equals(previewQuery.getImportResourceId())));
    }

    @Test
    void previewRoutesTheResourceIdToTheCommandBus() {
        CommandBus commandBus = mock(CommandBus.class);
        UUID importResourceId = UUID.randomUUID();
        ImportResourceController controller = controller(mock(QueryBus.class), commandBus);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse("ok"));

        var response = controller.previewImportResource(importResourceId);

        assertEquals(200, response.getStatusCode().value());
        verify(commandBus).push(argThat(command -> command instanceof StartImportPreviewCommand previewCommand
                && importResourceId.equals(previewCommand.getImportResourceId())));
    }

    @Test
    void startProcessRoutesTheResourceIdToTheCommandBusAndReturnsAcceptedOnSuccess() {
        CommandBus commandBus = mock(CommandBus.class);
        UUID importResourceId = UUID.randomUUID();
        ImportResourceController controller = controller(mock(QueryBus.class), commandBus);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse("ok"));

        var response = controller.startImportProcess(importResourceId);

        assertEquals(202, response.getStatusCode().value());
        verify(commandBus).push(argThat(command -> command instanceof StartImportProcessCommand processCommand
                && importResourceId.equals(processCommand.getImportResourceId())));
    }

    @Test
    void startProcessKeepsTheExistingGuardResponseShapeWhenTheCommandFails() {
        CommandBus commandBus = mock(CommandBus.class);
        UUID importResourceId = UUID.randomUUID();
        ImportResourceController controller = controller(mock(QueryBus.class), commandBus);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.failResponse("already processing"));

        var response = controller.startImportProcess(importResourceId);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void processStatusPollsTheRunStatusQueryAndReturnsOkWhenFound() {
        QueryBus queryBus = mock(QueryBus.class);
        UUID runId = UUID.randomUUID();
        ImportResourceController controller = controller(queryBus, mock(CommandBus.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse("ok"));

        var response = controller.findImportProcessStatus(runId);

        assertEquals(200, response.getStatusCode().value());
        verify(queryBus).push(argThat(query -> query instanceof FindImportRunStatusQuery runQuery
                && runId.equals(runQuery.getRunId())));
    }

    @Test
    void processStatusReturnsNotFoundWhenTheRunIsUnknown() {
        QueryBus queryBus = mock(QueryBus.class);
        UUID runId = UUID.randomUUID();
        ImportResourceController controller = controller(queryBus, mock(CommandBus.class));
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse("missing"));

        var response = controller.findImportProcessStatus(runId);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void uploadForwardsTheDefaultNoShrinkFlagAndReturnsAccepted() {
        ResourceUploadService resourceUploadService = mock(ResourceUploadService.class);
        ImportResourceController controller = controller(mock(QueryBus.class), mock(CommandBus.class),
                resourceUploadService);
        MockMultipartFile file = new MockMultipartFile("file", "season.zip",
                "multipart/form-data", new byte[]{1});

        var response = controller.uploadZipFile(file, false);

        assertEquals(202, response.getStatusCode().value());
        verify(resourceUploadService).uploadAndTriggerAsyncLoad("season.zip", new byte[]{1}, false);
    }

    @Test
    void uploadForwardsThePublishedShrinkOverride() {
        ResourceUploadService resourceUploadService = mock(ResourceUploadService.class);
        ImportResourceController controller = controller(mock(QueryBus.class), mock(CommandBus.class),
                resourceUploadService);
        MockMultipartFile file = new MockMultipartFile("file", "season.zip",
                "multipart/form-data", new byte[]{1});

        var response = controller.uploadZipFile(file, true);

        assertEquals(202, response.getStatusCode().value());
        verify(resourceUploadService).uploadAndTriggerAsyncLoad("season.zip", new byte[]{1}, true);
    }

    @Test
    void uploadMapsAShrinkRejectionToConflictWithTheMessage() {
        ResourceUploadService resourceUploadService = mock(ResourceUploadService.class);
        ImportResourceController controller = controller(mock(QueryBus.class), mock(CommandBus.class),
                resourceUploadService);
        MockMultipartFile file = new MockMultipartFile("file", "season.zip",
                "multipart/form-data", new byte[]{1});
        SnapshotShrinkException rejection = new SnapshotShrinkException(
                List.of(new SnapshotShrinkException.SeasonShrink("FCTT", "2026-2027", 76, 70)));
        doThrow(rejection).when(resourceUploadService)
                .uploadAndTriggerAsyncLoad("season.zip", new byte[]{1}, false);

        var response = controller.uploadZipFile(file, false);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(Map.of("message", rejection.getMessage()), response.getBody());
    }

    @Test
    void uploadKeepsBadRequestForMalformedUploads() {
        ResourceUploadService resourceUploadService = mock(ResourceUploadService.class);
        ImportResourceController controller = controller(mock(QueryBus.class), mock(CommandBus.class),
                resourceUploadService);
        MockMultipartFile file = new MockMultipartFile("file", "season.zip",
                "multipart/form-data", new byte[]{1});
        doThrow(new IllegalArgumentException("Only ZIP files are supported")).when(resourceUploadService)
                .uploadAndTriggerAsyncLoad("season.zip", new byte[]{1}, false);

        var response = controller.uploadZipFile(file, false);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(Map.of("message", "Only ZIP files are supported"), response.getBody());
    }

    private static ImportResourceController controller(QueryBus queryBus, CommandBus commandBus) {
        return controller(queryBus, commandBus, mock(ResourceUploadService.class));
    }

    private static ImportResourceController controller(QueryBus queryBus, CommandBus commandBus,
                                                       ResourceUploadService resourceUploadService) {
        ImportResourceController controller = new ImportResourceController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", commandBus);
        ReflectionTestUtils.setField(controller, "resourceUploadService", resourceUploadService);
        return controller;
    }
}
