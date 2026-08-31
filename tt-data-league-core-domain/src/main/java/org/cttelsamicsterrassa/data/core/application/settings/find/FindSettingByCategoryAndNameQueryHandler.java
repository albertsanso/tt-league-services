package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class FindSettingByCategoryAndNameQueryHandler
        extends DomainQueryHandler<FindSettingByCategoryAndNameQuery, List<Setting>> {
    private final SettingFinderService settingFinderService;

    @Inject
    public FindSettingByCategoryAndNameQueryHandler(SettingFinderService settingFinderService) {
        this.settingFinderService = settingFinderService;
    }

    @Override
    public DomainQueryResponse<List<Setting>> handle(FindSettingByCategoryAndNameQuery query) {
        return DomainQueryResponse.sucessResponse(
                settingFinderService.findByCategoryAndName(query.getCategory(), query.getName()));
    }
}
