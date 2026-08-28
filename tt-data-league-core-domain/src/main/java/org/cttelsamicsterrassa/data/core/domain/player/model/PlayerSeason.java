package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerSeasonCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerSeasonDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerSeasonNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Optional;
import java.util.UUID;

/**
 * A player's registration for one season, keyed by federation licence.
 *
 * <p>{@code source} is part of the natural key together with {@code license} and {@code season}:
 * federation licences are source-specific, so the same licence value can identify different people
 * in different sources.</p>
 */
public class PlayerSeason extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;
    private final String licenseId;
    private final Optional<FederatedPlayer> federatedPlayer;
    private final Season season;

    private PlayerSeason(UUID id, ImportSource source, String name, String licenseId, Season season, FederatedPlayer player) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.licenseId = licenseId;
        this.federatedPlayer = Optional.ofNullable(player);
        this.season = season;
    }

    public static PlayerSeason createNew(ImportSource source, String name, String licenseId, FederatedPlayer player, Season season) {
        PlayerSeason playerSeason = new PlayerSeason(UUID.randomUUID(), source, name, licenseId, season, player);
        playerSeason.publishPlayerSeasonCreatedEvent();
        return playerSeason;
    }

    public static PlayerSeason createExisting(UUID id, ImportSource source, String name, String licenseId, FederatedPlayer player, Season season) {
        return of(id, source, name, licenseId, player, season);
    }

    private static PlayerSeason of(UUID id, ImportSource source, String name, String licenseId, FederatedPlayer player, Season season) {
        return new PlayerSeason(id, source, name, licenseId, season, player);
    }

    public void modifyName(String name) {
        this.name = name;
        publishPlayerSeasonNameModifiedEvent(name);
    }

    public PlayerSeason withFederatedPlayer(FederatedPlayer player) {
        if (sameFederatedPlayer(player)) {
            return this;
        }
        return of(id, source, name, licenseId, player, season);
    }

    private boolean sameFederatedPlayer(FederatedPlayer other) {
        return federatedPlayer.map(current -> other != null && current.getId().equals(other.getId()))
                .orElse(other == null);
    }

    public void delete() {
        publishPlayerSeasonDeletedEvent();
    }

    private void publishPlayerSeasonCreatedEvent() {
        publishEvent(PlayerSeasonCreatedEvent.of(
                this.id, this.name, this.season, this.licenseId, this.source, federatedPlayer.orElse(null)));
    }

    private void publishPlayerSeasonNameModifiedEvent(String name) {
        publishEvent(PlayerSeasonNameModifiedEvent.of(this.id, name));
    }

    private void publishPlayerSeasonDeletedEvent() {
        publishEvent(PlayerSeasonDeletedEvent.of(this.id));
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

    public Optional<FederatedPlayer> getFederatedPlayer() {
        return federatedPlayer;
    }

    public Season getSeason() {
        return season;
    }

    public String getLicenseId() {
        return licenseId;
    }

    /** @deprecated use {@link #getLicenseId()} */
    @Deprecated
    public String getLicense() {
        return licenseId;
    }
}
