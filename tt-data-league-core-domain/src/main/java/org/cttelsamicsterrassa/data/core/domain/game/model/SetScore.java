package org.cttelsamicsterrassa.data.core.domain.game.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public class SetScore extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final Game game;
    private final int setNumber;
    private final int homePoints;
    private final int awayPoints;

    private SetScore(UUID id, ImportSource source, Game game, int setNumber, int homePoints, int awayPoints) {
        this.id = id;
        this.source = source;
        this.game = game;
        this.setNumber = setNumber;
        this.homePoints = homePoints;
        this.awayPoints = awayPoints;
    }

    public static final SetScoreBuilder builder() {
        return new SetScoreBuilder();
    }

    public static final class SetScoreBuilder {
        private UUID id;
        private ImportSource source;
        private Game game;
        private int setNumber;
        private int homePoints;
        private int awayPoints;

        public SetScoreBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public SetScoreBuilder source(ImportSource source) {
            this.source = source;
            return this;
        }

        public SetScoreBuilder game(Game game) {
            this.game = game;
            return this;
        }

        public SetScoreBuilder setNumber(int setNumber) {
            this.setNumber = setNumber;
            return this;
        }

        public SetScoreBuilder homePoints(int homePoints) {
            this.homePoints = homePoints;
            return this;
        }

        public SetScoreBuilder awayPoints(int awayPoints) {
            this.awayPoints = awayPoints;
            return this;
        }

        public SetScore build() {
            return new SetScore(id, source, game, setNumber, homePoints, awayPoints);
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

    public int getSetNumber() {
        return setNumber;
    }

    public int getHomePoints() {
        return homePoints;
    }

    public int getAwayPoints() {
        return awayPoints;
    }
}
