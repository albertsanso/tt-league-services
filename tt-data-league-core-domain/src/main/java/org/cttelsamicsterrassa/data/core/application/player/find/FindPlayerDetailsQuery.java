package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindPlayerDetailsQuery extends DomainQuery {
    private final UUID playerId;
    private final ImportSource source;
    private final Season season;
    private final String competition;

    public FindPlayerDetailsQuery(UUID playerId) {
        this(playerId, null, null, null);
    }

    public FindPlayerDetailsQuery(UUID playerId, ImportSource source, Season season, String competition) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.playerId = playerId;
        this.source = source;
        this.season = season;
        this.competition = competition;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ImportSource getSource() {
        return source;
    }

    public Season getSeason() {
        return season;
    }

    public String getCompetition() {
        return competition;
    }
}
