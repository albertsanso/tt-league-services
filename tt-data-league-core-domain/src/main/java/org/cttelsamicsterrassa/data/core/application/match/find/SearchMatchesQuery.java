package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;

import java.time.ZonedDateTime;
import java.util.UUID;

public class SearchMatchesQuery extends DomainQuery {
    private final MatchSearchCriteria criteria;

    public SearchMatchesQuery(MatchSearchCriteria criteria) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.criteria = criteria;
    }

    public MatchSearchCriteria getCriteria() {
        return criteria;
    }
}
