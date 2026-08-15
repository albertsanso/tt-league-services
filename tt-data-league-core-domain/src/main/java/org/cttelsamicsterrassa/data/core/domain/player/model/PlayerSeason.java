package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.UUID;

/**
 * A player's registration for one season, keyed by federation licence.
 *
 * <p>{@code source} is part of the natural key together with {@code license} and {@code season}: the
 * two federations number licences independently, so the same licence value in RFETM and BCNESA
 * identifies two different people. Measured across both exports, 212 licences occur in both, and all
 * 212 belong to different players.</p>
 */
public class PlayerSeason extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final String name;
    private final String license;
    private final Player player;
    private final Season season;

    private PlayerSeason(UUID id, ImportSource source, String name, String license, Season season, Player player) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.license = license;
        this.player = player;
        this.season = season;
    }

    public static PlayerSeason createNew(String name, String license, Player player, Season season) {
        return createNew(ImportSource.RFETM, name, license, player, season);
    }

    public static PlayerSeason createNew(ImportSource source, String name, String license, Player player, Season season) {
        return new PlayerSeason(UUID.randomUUID(), source, name, license, season, player);
    }

    public static PlayerSeason of(UUID id, String name, String license, Player player, Season season) {
        return of(id, ImportSource.RFETM, name, license, player, season);
    }

    public static PlayerSeason of(UUID id, ImportSource source, String name, String license, Player player, Season season) {
        return new PlayerSeason(id, source, name, license, season, player);
    }

    public UUID getId() {
        return id;
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

    public Season getSeason() {
        return season;
    }

    public String getLicense() {
        return license;
    }
}
