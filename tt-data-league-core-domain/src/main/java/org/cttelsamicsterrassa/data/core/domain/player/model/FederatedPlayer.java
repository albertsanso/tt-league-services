package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;
import java.util.Optional;
import java.util.Objects;

public class FederatedPlayer extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;
    private final String licenseId;
    private final Optional<Player> player;

    private FederatedPlayer(UUID id, ImportSource source, String name) {
        this(id, source, name, null, null);
    }

    private FederatedPlayer(UUID id, ImportSource source, String name, Player player) {
        this(id, source, name, null, player);
    }

    private FederatedPlayer(UUID id, ImportSource source, String name, String licenseId, Player player) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.licenseId = licenseId;
        this.player = Optional.ofNullable(player);
    }

    public static FederatedPlayer createNew(ImportSource source, String name) {
        return createNew(source, name, (Player) null);
    }

    public static FederatedPlayer createNew(ImportSource source, String name, Player player) {
        return createNew(source, name, null, player);
    }

    public static FederatedPlayer createNew(ImportSource source, String name, String licenseId) {
        return createNew(source, name, licenseId, null);
    }

    public static FederatedPlayer createNew(ImportSource source, String name, String licenseId, Player player) {
        FederatedPlayer federatedPlayer = of(UUID.randomUUID(), source, name, licenseId, player);
        federatedPlayer.publishFederatedPlayerCreatedEvent();
        return federatedPlayer;
    }

    public static FederatedPlayer createExisting(UUID id, ImportSource source, String name) {
        return createExisting(id, source, name, null);
    }

    public static FederatedPlayer createExisting(UUID id, ImportSource source, String name, Player player) {
        return createExisting(id, source, name, null, player);
    }

    public static FederatedPlayer createExisting(UUID id, ImportSource source, String name, String licenseId, Player player) {
        return of(id, source, name, licenseId, player);
    }

    private static FederatedPlayer of(UUID id, ImportSource source, String name, String licenseId, Player player) {
        return new FederatedPlayer(id, source, name, licenseId, player);
    }

    public FederatedPlayer withPlayer(Player player) {
        if (player != null && this.player.map(existing -> existing.getId().equals(player.getId())).orElse(false)) {
            return this;
        }
        return of(id, source, name, licenseId, player);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishFederatedPlayerNameModifiedEvent();
        }
    }

    public void delete() {
        publishFederatedPlayerDeletedEvent();
    }

    private void publishFederatedPlayerCreatedEvent() {
        publishEvent(FederatedPlayerCreatedEvent.of(id, name));
    }

    private void publishFederatedPlayerNameModifiedEvent() {
        publishEvent(FederatedPlayerNameModifiedEvent.of(id.toString(), name));
    }

    private void publishFederatedPlayerDeletedEvent() {
        publishEvent(FederatedPlayerDeletedEvent.of(id.toString()));
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

    public String getLicenseId() {
        return licenseId;
    }

    public FederatedPlayer withLicenseId(String licenseId) {
        if (Objects.equals(this.licenseId, licenseId)) {
            return this;
        }
        return of(id, source, name, licenseId, player.orElse(null));
    }

    public Optional<Player> getPlayer() {
        return player;
    }
}
