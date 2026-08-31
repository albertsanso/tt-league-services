package org.cttelsamicsterrassa.data.api.rest.settings;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsControllerTest {
    private static final UUID SETTING_ID = UUID.randomUUID();

    @Test
    void routesQueriesToTheQueryBus() {
        QueryBus queryBus = mock(QueryBus.class);
        CommandBus commandBus = mock(CommandBus.class);
        Setting setting = Setting.createExisting(SETTING_ID, SettingCategory.GENERAL, "site.name", "League");
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of(setting)));
        SettingsController controller = new SettingsController(queryBus, commandBus);

        var response = controller.findSettings("general");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(new SettingDto(SETTING_ID, "GENERAL", "site.name", "League")), response.getBody());
        verify(queryBus).push(any());
    }

    @Test
    void routesCommandsToTheCommandBus() {
        QueryBus queryBus = mock(QueryBus.class);
        CommandBus commandBus = mock(CommandBus.class);
        Setting setting = Setting.createExisting(SETTING_ID, SettingCategory.GENERAL, "site.name", "League");
        when(commandBus.push(any())).thenReturn(DomainCommandResponse.successResponse(setting));
        SettingsController controller = new SettingsController(queryBus, commandBus);

        var response = controller.updateSetting(SETTING_ID, new UpdateSettingRequest("Updated"));

        assertEquals(200, response.getStatusCode().value());
        verify(commandBus).push(any());
    }
}
