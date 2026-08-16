package org.cttelsamicsterrassa.data.core.domain.game.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.game.event.GameCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.game.event.GameDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public class Game extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final Match match;
    private final int gameNumber;
    private final String type;
    private final String crossover;
    private final PlayerSeason homePlayer;
    private final PlayerSeason awayPlayer;
    private final Integer homeSetsWon;
    private final Integer awaySetsWon;
    private final PlayerSeason winner;
    private final String winnerSide;
    private final int cumulativeHomeSetsWon;
    private final int cumulativeAwaySetsWon;
    private final boolean notPlayed;
    private final String reason;

    private Game(UUID id, ImportSource source, Match match, int gameNumber, String type, String crossover, PlayerSeason homePlayer, PlayerSeason awayPlayer, Integer homeSetsWon, Integer awaySetsWon, PlayerSeason winner, String winnerSide, int cumulativeHomeSetsWon, int cumulativeAwaySetsWon, boolean notPlayed, String reason) {
        this.id = id;
        this.source = source;
        this.match = match;
        this.gameNumber = gameNumber;
        this.type = type;
        this.crossover = crossover;
        this.homePlayer = homePlayer;
        this.awayPlayer = awayPlayer;
        this.homeSetsWon = homeSetsWon;
        this.awaySetsWon = awaySetsWon;
        this.winner = winner;
        this.winnerSide = winnerSide;
        this.cumulativeHomeSetsWon = cumulativeHomeSetsWon;
        this.cumulativeAwaySetsWon = cumulativeAwaySetsWon;
        this.notPlayed = notPlayed;
        this.reason = reason;
    }

    public static GameBuilder builder() {
        return new GameBuilder();
    }

    private static Game of(GameBuilder builder) {
        return new Game(
                builder.id,
                builder.source,
                builder.match,
                builder.gameNumber,
                builder.type,
                builder.crossover,
                builder.homePlayer,
                builder.awayPlayer,
                builder.homeSetsWon,
                builder.awaySetsWon,
                builder.winner,
                builder.winnerSide,
                builder.cumulativeHomeSetsWon,
                builder.cumulativeAwaySetsWon,
                builder.notPlayed,
                builder.reason);
    }

    private static Game createNew(GameBuilder gameBuilder) {
        Game game = of(gameBuilder);
        game.publishGameCreatedEvent();
        return game;
    }

    private static Game createExisting(GameBuilder gameBuilder) {
        return of(gameBuilder);
    }

    public void delete() {
        publishGameDeletedEvent();
    }

    private void publishGameCreatedEvent() {
        publishEvent(GameCreatedEvent.of(this.id));
    }

    private void publishGameDeletedEvent() {
        publishEvent(GameDeletedEvent.of(this.id));
    }

    public static final class GameBuilder {
        private UUID id;
        private ImportSource source;
        private Match match;
        private int gameNumber;
        private String type;
        private String crossover;
        private PlayerSeason homePlayer;
        private PlayerSeason awayPlayer;
        private Integer homeSetsWon;
        private Integer awaySetsWon;
        private PlayerSeason winner;
        private String winnerSide;
        private int cumulativeHomeSetsWon;
        private int cumulativeAwaySetsWon;
        private boolean notPlayed;
        private String reason;

        public GameBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public GameBuilder source(ImportSource source) {
            this.source = source;
            return this;
        }

        public GameBuilder match(Match match) {
            this.match = match;
            return this;
        }

        public GameBuilder gameNumber(int gameNumber) {
            this.gameNumber = gameNumber;
            return this;
        }

        public GameBuilder type(String type) {
            this.type = type;
            return this;
        }

        public GameBuilder crossover(String crossover) {
            this.crossover = crossover;
            return this;
        }

        public GameBuilder homePlayer(PlayerSeason homePlayer) {
            this.homePlayer = homePlayer;
            return this;
        }

        public GameBuilder awayPlayer(PlayerSeason awayPlayer) {
            this.awayPlayer = awayPlayer;
            return this;
        }

        public GameBuilder homeSetsWon(Integer homeSetsWon) {
            this.homeSetsWon = homeSetsWon;
            return this;
        }

        public GameBuilder awaySetsWon(Integer awaySetsWon) {
            this.awaySetsWon = awaySetsWon;
            return this;
        }

        public GameBuilder winner(PlayerSeason winner) {
            this.winner = winner;
            return this;
        }

        public GameBuilder winnerSide(String winnerSide) {
            this.winnerSide = winnerSide;
            return this;
        }

        public GameBuilder cumulativeHomeSetsWon(int cumulativeHomeSetsWon) {
            this.cumulativeHomeSetsWon = cumulativeHomeSetsWon;
            return this;
        }

        public GameBuilder cumulativeAwaySetsWon(int cumulativeAwaySetsWon) {
            this.cumulativeAwaySetsWon = cumulativeAwaySetsWon;
            return this;
        }

        public GameBuilder notPlayed(boolean notPlayed) {
            this.notPlayed = notPlayed;
            return this;
        }

        public GameBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Game createNew() {
            return Game.createNew(this);
        }

        public Game createExisting() {
            return Game.createExisting(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public Match getMatch() {
        return match;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    public String getType() {
        return type;
    }

    public String getCrossover() {
        return crossover;
    }

    public PlayerSeason getHomePlayer() {
        return homePlayer;
    }

    public PlayerSeason getAwayPlayer() {
        return awayPlayer;
    }

    public Integer getHomeSetsWon() {
        return homeSetsWon;
    }

    public Integer getAwaySetsWon() {
        return awaySetsWon;
    }

    public PlayerSeason getWinner() {
        return winner;
    }

    /**
     * Which side won, as {@code HOME} or {@code AWAY}, or {@code null} if the game was not played.
     *
     * <p>This is the authoritative outcome. {@link #getWinner()} names the winning player, which
     * only exists for singles: a doubles game is won by a pair, whose members are held in
     * {@link DoublesPair}.</p>
     */
    public String getWinnerSide() {
        return winnerSide;
    }

    public int getCumulativeHomeSetsWon() {
        return cumulativeHomeSetsWon;
    }

    public int getCumulativeAwaySetsWon() {
        return cumulativeAwaySetsWon;
    }

    public boolean isNotPlayed() {
        return notPlayed;
    }

    public String getReason() {
        return reason;
    }
}
