package org.cttelsamicsterrassa.data.core.domain.game.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public class DoublesPair extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final Game game;
    private final String side;
    private final PlayerSeason player;

    private DoublesPair(UUID id, ImportSource source, Game game, String side, PlayerSeason player) {
        this.id = id;
        this.source = source;
        this.game = game;
        this.side = side;
        this.player = player;
    }

    public static final DoublesPairBuilder builder() {
        return new DoublesPairBuilder();
    }

    public static final class DoublesPairBuilder {
        private UUID id;
        private ImportSource source;
        private Game game;
        private String side;
        private PlayerSeason player;

        public DoublesPairBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public DoublesPairBuilder source(ImportSource source) {
            this.source = source;
            return this;
        }

        public DoublesPairBuilder game(Game game) {
            this.game = game;
            return this;
        }

        public DoublesPairBuilder side(String side) {
            this.side = side;
            return this;
        }

        public DoublesPairBuilder player(PlayerSeason player) {
            this.player = player;
            return this;
        }

        public DoublesPair build() {
            return new DoublesPair(id, source, game, side, player);
        }
    }

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public Game getGame() {
        return game;
    }

    public String getSide() {
        return side;
    }

    public PlayerSeason getPlayer() {
        return player;
    }
}
