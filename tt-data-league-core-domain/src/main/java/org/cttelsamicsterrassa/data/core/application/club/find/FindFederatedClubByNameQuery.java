package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public class FindFederatedClubByNameQuery extends DomainQuery {

    private final String federatedClubName;
    private final ImportSource source;

    public FindFederatedClubByNameQuery(
            ZonedDateTime occurredOn, ImportSource source, String federatedClubName) {
        super(occurredOn, UUID.randomUUID().toString());
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.federatedClubName = federatedClubName;
    }

    public FindFederatedClubByNameQuery(
            ZonedDateTime occurredOn, String federatedClubName, ImportSource source) {
        this(occurredOn, source, federatedClubName);
    }

    public ImportSource getSource() {
        return source;
    }

    public String getFederatedClubName() {
        return federatedClubName;
    }
}
