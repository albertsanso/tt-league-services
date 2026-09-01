package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Optional;

@Named
public class FindSettingByCategoryAndNameQueryHandler
        extends DomainQueryHandler<FindSettingByCategoryAndNameQuery, Optional<Setting>> {
    private final SettingFinderService settingFinderService;

    @Inject
    public FindSettingByCategoryAndNameQueryHandler(SettingFinderService settingFinderService) {
        this.settingFinderService = settingFinderService;
    }

    @Override
    public DomainQueryResponse<Optional<Setting>> handle(FindSettingByCategoryAndNameQuery query) {
        return DomainQueryResponse.sucessResponse(
                settingFinderService.findByCategoryAndName(query.getCategory(), query.getName()));
    }
}
