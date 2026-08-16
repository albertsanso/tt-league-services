package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PlayerSeasonCreatedEvent extends DomainEvent {

    private final UUID playerSeasonId;
    private final String name;
    private final Season season;
    private final String license;
    private final ImportSource source;
    private final Player player;

    private PlayerSeasonCreatedEvent(UUID playerSeasonId, String name, Season season, String license, ImportSource source, Player player) {
        super(ZonedDateTime.now(), playerSeasonId.toString());
        this.playerSeasonId = playerSeasonId;
        this.name = name;
        this.season = season;
        this.license = license;
        this.source = source;
        this.player = player;
    }

    public static PlayerSeasonCreatedEvent of(UUID playerSeasonId, String name, Season season, String license, ImportSource source, Player player) {
        return new PlayerSeasonCreatedEvent(playerSeasonId, name, season, license, source, player);
    }

    public UUID getPlayerSeasonId() {
        return playerSeasonId;
    }

    public Season getSeason() {
        return season;
    }

    public String getLicense() {
        return license;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getName() {
        return name;
    }

    public Player getPlayer() {
        return player;
    }
}
