package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindSettingsByCategoryQueryHandler extends DomainQueryHandler<FindSettingsByCategoryQuery, List<Setting>> {
    private final SettingFinderService settingFinderService;

    @Inject
    public FindSettingsByCategoryQueryHandler(SettingFinderService settingFinderService) {
        this.settingFinderService = settingFinderService;
    }

    @Override
    public DomainQueryResponse<List<Setting>> handle(FindSettingsByCategoryQuery query) {
        return DomainQueryResponse.sucessResponse(settingFinderService.findByCategory(query.getCategory()));
    }
}
