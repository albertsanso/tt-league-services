package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;
import java.util.Optional;

public class FederatedPlayer extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;
    private final Optional<Player> player;

    private FederatedPlayer(UUID id, ImportSource source, String name) {
        this(id, source, name, null);
    }

    private FederatedPlayer(UUID id, ImportSource source, String name, Player player) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.player = Optional.ofNullable(player);
    }

    public static FederatedPlayer createNew(ImportSource source, String name) {
        return createNew(source, name, null);
    }

    public static FederatedPlayer createNew(ImportSource source, String name, Player player) {
        FederatedPlayer federatedPlayer = of(UUID.randomUUID(), source, name, player);
        federatedPlayer.publishFederatedPlayerCreatedEvent();
        return federatedPlayer;
    }

    public static FederatedPlayer createExisting(UUID id, ImportSource source, String name) {
        return createExisting(id, source, name, null);
    }

    public static FederatedPlayer createExisting(UUID id, ImportSource source, String name, Player player) {
        return of(id, source, name, player);
    }

    private static FederatedPlayer of(UUID id, ImportSource source, String name, Player player) {
        return new FederatedPlayer(id, source, name, player);
    }

    public FederatedPlayer withPlayer(Player player) {
        if (player != null && this.player.map(existing -> existing.getId().equals(player.getId())).orElse(false)) {
            return this;
        }
        return of(id, source, name, player);
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

    public Optional<Player> getPlayer() {
        return player;
    }
}
