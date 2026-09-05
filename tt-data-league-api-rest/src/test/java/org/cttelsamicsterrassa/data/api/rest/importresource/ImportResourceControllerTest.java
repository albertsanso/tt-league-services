package org.cttelsamicsterrassa.data.api.rest.importresource;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.importresource.find.FindPendingImportsInfoQuery;
import org.cttelsamicsterrassa.data.core.application.importresource.find.dto.PendingImportsInfoDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.FindImportPreviewStatusQuery;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.StartImportPreviewCommand;
import org.cttelsamicsterrassa.data.core.application.importresource.process.StartImportProcessCommand;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    void startProcessRoutesTheResourceIdToTheCommandBus() {
        CommandBus commandBus = mock(CommandBus.class);
        UUID importResourceId = UUID.randomUUID();
        ImportResourceController controller = controller(mock(QueryBus.class), commandBus);
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse("ok"));

        var response = controller.startImportProcess(importResourceId);

        assertEquals(200, response.getStatusCode().value());
        verify(commandBus).push(argThat(command -> command instanceof StartImportProcessCommand processCommand
                && importResourceId.equals(processCommand.getImportResourceId())));
    }

    private static ImportResourceController controller(QueryBus queryBus, CommandBus commandBus) {
        ImportResourceController controller = new ImportResourceController();
        ReflectionTestUtils.setField(controller, "queryBus", queryBus);
        ReflectionTestUtils.setField(controller, "commandBus", commandBus);
        return controller;
    }
}
