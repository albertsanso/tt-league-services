package org.cttelsamicsterrassa.data.core.application.stats.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Requests the community-wide statistics overview: total players, clubs, matches, and the current
 * season, across every import source.
 */
public class FindCommunityStatisticsQuery extends DomainQuery {

    public FindCommunityStatisticsQuery() {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
    }
}
