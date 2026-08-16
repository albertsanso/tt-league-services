package org.cttelsamicsterrassa.data.core.domain.lineup.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.lineup.event.LineupCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.lineup.event.LineupDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

/**
 * One player's assignment in a match lineup.
 *
 * <p>{@code ranking} is a point-in-time snapshot and is nullable: most RFETM match reports carry no
 * ranking at all, and zero is not a usable stand-in for "unknown" on a scale that starts around
 * 1500.</p>
 */
public class Lineup extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final Match match;
    private final ClubSeason clubSeason;
    private final String letter;
    private final int position;
    private final PlayerSeason player;
    private final Float ranking;

    private Lineup(UUID id, ImportSource source, Match match, ClubSeason clubSeason, String letter, int position, PlayerSeason player, Float ranking) {
        this.id = id;
        this.source = source;
        this.match = match;
        this.clubSeason = clubSeason;
        this.letter = letter;
        this.position = position;
        this.player = player;
        this.ranking = ranking;
    }

    public static LineupBuilder builder() {
        return new LineupBuilder();
    }

    private static Lineup of(UUID id, ImportSource source, Match match, ClubSeason clubSeason, String letter, int position, PlayerSeason player, Float ranking) {
        return new Lineup(id, source, match, clubSeason, letter, position, player, ranking);
    }

    private static Lineup createNew(LineupBuilder builder) {
        Lineup lineup = of(UUID.randomUUID(), builder.source, builder.match, builder.clubSeason, builder.letter, builder.position, builder.player, builder.ranking);
        lineup.publishLineupCreatedEvent();
        return lineup;
    }

    private static Lineup createExisting(LineupBuilder builder) {
        return of(builder.id, builder.source, builder.match, builder.clubSeason, builder.letter, builder.position, builder.player, builder.ranking);
    }

    public void delete() {
        publishLineupDeletedEvent();
    }

    private void publishLineupCreatedEvent() {
        publishEvent(LineupCreatedEvent.of(id));}

    private void publishLineupDeletedEvent() {
        publishEvent(LineupDeletedEvent.of(id));
    }

    public static final class LineupBuilder {
        private UUID id;
        private ImportSource source;
        private Match match;
        private ClubSeason clubSeason;
        private String letter;
        private int position;
        private PlayerSeason player;
        private Float ranking;

        public LineupBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public LineupBuilder source(ImportSource source) {
            this.source = source;
            return this;
        }

        public LineupBuilder match(Match match) {
            this.match = match;
            return this;
        }

        public LineupBuilder clubSeason(ClubSeason clubSeason) {
            this.clubSeason = clubSeason;
            return this;
        }

        public LineupBuilder letter(String letter) {
            this.letter = letter;
            return this;
        }

        public LineupBuilder position(int position) {
            this.position = position;
            return this;
        }

        public LineupBuilder player(PlayerSeason player) {
            this.player = player;
            return this;
        }

        public LineupBuilder ranking(Float ranking) {
            this.ranking = ranking;
            return this;
        }

        public Lineup createNew() {
            return Lineup.createNew(this);
        }

        public Lineup createExisting() {
            return Lineup.createExisting(this);
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

    public ClubSeason getClubSeason() {
        return clubSeason;
    }

    public String getLetter() {
        return letter;
    }

    public int getPosition() {
        return position;
    }

    public PlayerSeason getPlayer() {
        return player;
    }

    public Float getRanking() {
        return ranking;
    }
}
