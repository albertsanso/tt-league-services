package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public class TeamCreatedEvent extends DomainEvent {
    private final UUID teamId;
    private final String teamName;
    private final ImportSource source;
    private final Optional<FederatedClub> federatedClub;

    private TeamCreatedEvent(UUID teamId, String teamName, ImportSource source, FederatedClub club) {
        super(ZonedDateTime.now(), teamId.toString());
        this.teamId = teamId;
        this.teamName = teamName;
        this.source = source;
        this.federatedClub = Optional.ofNullable(club);
    }

    public static TeamCreatedEvent of(UUID teamId, String teamName, ImportSource source, FederatedClub club) {
        return new TeamCreatedEvent(teamId, teamName, source, club);
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public Optional<FederatedClub> getFederatedClub() {
        return federatedClub;
    }

    public ImportSource getSource() {
        return source;
    }
}
