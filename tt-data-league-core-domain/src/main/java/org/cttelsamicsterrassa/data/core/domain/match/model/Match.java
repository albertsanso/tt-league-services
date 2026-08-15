package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

public class Match extends Entity {

    /**
     * The zone match dates and times are expressed in. Match reports record a local wall-clock
     * date and time with no offset; the RFETM runs its competitions on peninsular Spanish time, so
     * that is the zone used whenever a report is turned into an instant and back.
     */
    public static final ZoneId COMPETITION_ZONE = ZoneId.of("Europe/Madrid");

    private final UUID id;
    private final ImportSource source;
    private final String externalId;
    private final String competition;
    private final Season season;
    private final int groupNumber;
    private final int round;
    private final ZonedDateTime dateTime;
    private final String city;
    private final String venue;
    private final ClubSeason homeClub;
    private final ClubSeason awayClub;
    private final ClubSeason winnerClub;
    private final String refereeName;
    private final Integer homeGamesWon;
    private final Integer awayGamesWon;
    private final Integer homeSetsWon;
    private final Integer awaySetsWon;
    private final boolean protested;

    private Match(UUID id, ImportSource source, String externalId, String competition, Season season, int groupNumber, int round, ZonedDateTime dateTime, String city, String venue, ClubSeason homeClub, ClubSeason awayClub, ClubSeason winnerClub, String refereeName, Integer homeGamesWon, Integer awayGamesWon, Integer homeSetsWon, Integer awaySetsWon, boolean protested) {
        this.id = id;
        this.source = source;
        this.externalId = externalId;
        this.competition = competition;
        this.season = season;
        this.groupNumber = groupNumber;
        this.round = round;
        this.dateTime = dateTime;
        this.city = city;
        this.venue = venue;
        this.homeClub = homeClub;
        this.awayClub = awayClub;
        this.winnerClub = winnerClub;
        this.refereeName = refereeName;
        this.homeGamesWon = homeGamesWon;
        this.awayGamesWon = awayGamesWon;
        this.homeSetsWon = homeSetsWon;
        this.awaySetsWon = awaySetsWon;
        this.protested = protested;
    }

    public static final MatchBuilder builder() {
        return new MatchBuilder();
    }

    public static final class MatchBuilder {
        private UUID id;
        private ImportSource source = ImportSource.RFETM;
        private String externalId;
        private String competition;
        private Season season;
        private int groupNumber;
        private int round;
        private ZonedDateTime dateTime;
        private String city;
        private String venue;
        private ClubSeason homeClub;
        private ClubSeason awayClub;
        private ClubSeason winnerClub;
        private String refereeName;
        private Integer homeGamesWon;
        private Integer awayGamesWon;
        private Integer homeSetsWon;
        private Integer awaySetsWon;
        private boolean protested;

        public MatchBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public MatchBuilder source(ImportSource source) {
            this.source = source;
            return this;
        }

        public MatchBuilder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public MatchBuilder competition(String competition) {
            this.competition = competition;
            return this;
        }

        public MatchBuilder season(Season season) {
            this.season = season;
            return this;
        }

        public MatchBuilder groupNumber(int groupNumber) {
            this.groupNumber = groupNumber;
            return this;
        }

        public MatchBuilder round(int round) {
            this.round = round;
            return this;
        }

        public MatchBuilder dateTime(ZonedDateTime dateTime) {
            this.dateTime = dateTime;
            return this;
        }

        public MatchBuilder city(String city) {
            this.city = city;
            return this;
        }

        public MatchBuilder venue(String venue) {
            this.venue = venue;
            return this;
        }

        public MatchBuilder homeClub(ClubSeason homeClub) {
            this.homeClub = homeClub;
            return this;
        }

        public MatchBuilder awayClub(ClubSeason awayClub) {
            this.awayClub = awayClub;
            return this;
        }

        public MatchBuilder refereeName(String refereeName) {
            this.refereeName = refereeName;
            return this;
        }

        public MatchBuilder homeGamesWon(Integer homeGamesWon) {
            this.homeGamesWon = homeGamesWon;
            return this;
        }

        public MatchBuilder awayGamesWon(Integer awayGamesWon) {
            this.awayGamesWon = awayGamesWon;
            return this;
        }

        public MatchBuilder homeSetsWon(Integer homeSetsWon) {
            this.homeSetsWon = homeSetsWon;
            return this;
        }

        public MatchBuilder awaySetsWon(Integer awaySetsWon) {
            this.awaySetsWon = awaySetsWon;
            return this;
        }

        public MatchBuilder protested(boolean protested) {
            this.protested = protested;
            return this;
        }

        public MatchBuilder winnerClub(ClubSeason winnerClub) {
            this.winnerClub = winnerClub;
            return this;
        }

        public Match build() {
            return new Match(
                    id,
                    source,
                    externalId,
                    competition,
                    season,
                    groupNumber,
                    round,
                    dateTime,
                    city,
                    venue,
                    homeClub,
                    awayClub,
                    winnerClub,
                    refereeName,
                    homeGamesWon,
                    awayGamesWon,
                    homeSetsWon,
                    awaySetsWon,
                    protested
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCompetition() {
        return competition;
    }

    public Season getSeason() {
        return season;
    }

    public int getGroupNumber() {
        return groupNumber;
    }

    public int getRound() {
        return round;
    }

    public ZonedDateTime getDateTime() {
        return dateTime;
    }

    public String getCity() {
        return city;
    }

    public String getVenue() {
        return venue;
    }

    public ClubSeason getHomeClub() {
        return homeClub;
    }

    public ClubSeason getAwayClub() {
        return awayClub;
    }

    public String getRefereeName() {
        return refereeName;
    }

    public Integer getHomeGamesWon() {
        return homeGamesWon;
    }

    public Integer getAwayGamesWon() {
        return awayGamesWon;
    }

    public Integer getHomeSetsWon() {
        return homeSetsWon;
    }

    public Integer getAwaySetsWon() {
        return awaySetsWon;
    }

    public boolean isProtested() {
        return protested;
    }

    public ClubSeason getWinnerClub() {
        return winnerClub;
    }
}
