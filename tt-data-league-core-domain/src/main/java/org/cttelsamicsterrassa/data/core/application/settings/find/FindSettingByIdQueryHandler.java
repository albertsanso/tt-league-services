package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindSettingByIdQueryHandler extends DomainQueryHandler<FindSettingByIdQuery, Setting> {
    private final SettingFinderService settingFinderService;

    @Inject
    public FindSettingByIdQueryHandler(SettingFinderService settingFinderService) {
        this.settingFinderService = settingFinderService;
    }

    @Override
    public DomainQueryResponse<Setting> handle(FindSettingByIdQuery query) {
        return DomainQueryResponse.sucessResponse(settingFinderService.findById(query.getSettingId()));
    }
}
