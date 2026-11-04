package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.event.MatchCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.match.event.MatchDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public class Match extends Entity {

    /**
     * The zone match dates and times are expressed in. Match reports record a local wall-clock
     * date and time with no offset; the RFETM runs its competitions on peninsular Spanish time, so
     * that is the zone used whenever a report is turned into an instant and back.
     */
    public static final ZoneId COMPETITION_ZONE = ZoneId.of("Europe/Madrid");

    /**
     * Maximum stored length of a source fixture id (FEAT-00083). The value is treated as an opaque
     * source key whose layout differs by source; it is never trimmed, padded or truncated.
     */
    public static final int SOURCE_FIXTURE_ID_MAX_LENGTH = 100;

    private final UUID id;
    private final ImportSource source;
    private final String externalId;
    private final String sourceFixtureId;
    private final String competition;
    private final Season season;
    private final Integer groupNumber;
    private final int round;
    private final String phase;
    private final ZonedDateTime dateTime;
    private final String city;
    private final String venue;
    private final Team homeTeam;
    private final Team awayTeam;
    private final Team winnerTeam;
    private final String refereeName;
    private final String refereeLicense;
    private final Integer homeGamesWon;
    private final Integer awayGamesWon;
    private final Integer homeSetsWon;
    private final Integer awaySetsWon;
    private final boolean protested;
    private final MatchStatus status;
    private final String sourceChecksum;

    private Match(UUID id, ImportSource source, String externalId, String sourceFixtureId, String competition, Season season, Integer groupNumber, int round, String phase, ZonedDateTime dateTime, String city, String venue, Team homeTeam, Team awayTeam, Team winnerTeam, String refereeName, String refereeLicense, Integer homeGamesWon, Integer awayGamesWon, Integer homeSetsWon, Integer awaySetsWon, boolean protested, MatchStatus status, String sourceChecksum) {
        this.id = id;
        this.source = source;
        this.externalId = externalId;
        this.sourceFixtureId = sourceFixtureId;
        this.competition = competition;
        this.season = season;
        this.groupNumber = groupNumber;
        this.round = round;
        this.phase = phase;
        this.dateTime = dateTime;
        this.city = city;
        this.venue = venue;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.winnerTeam = winnerTeam;
        this.refereeName = refereeName;
        this.refereeLicense = refereeLicense;
        this.homeGamesWon = homeGamesWon;
        this.awayGamesWon = awayGamesWon;
        this.homeSetsWon = homeSetsWon;
        this.awaySetsWon = awaySetsWon;
        this.protested = protested;
        this.status = status;
        this.sourceChecksum = sourceChecksum;
    }

    public static MatchBuilder builder() {
        return new MatchBuilder();
    }

    private static Match of(MatchBuilder builder) {
        Objects.requireNonNull(builder.status, "status");
        if (builder.status == MatchStatus.SCHEDULED
                && (builder.winnerTeam != null
                        || builder.homeGamesWon != null
                        || builder.awayGamesWon != null
                        || builder.homeSetsWon != null
                        || builder.awaySetsWon != null)) {
            throw new IllegalArgumentException("A SCHEDULED match cannot carry a winner or game/set results");
        }
        if (builder.status == MatchStatus.SCHEDULED && builder.sourceChecksum != null) {
            throw new IllegalArgumentException("A SCHEDULED match cannot carry a source checksum");
        }
        if (builder.sourceFixtureId != null) {
            if (builder.sourceFixtureId.isBlank()) {
                throw new IllegalArgumentException("sourceFixtureId must not be blank");
            }
            if (builder.sourceFixtureId.length() > SOURCE_FIXTURE_ID_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "sourceFixtureId must not be longer than " + SOURCE_FIXTURE_ID_MAX_LENGTH + " characters");
            }
        }
        return new Match(
                builder.id,
                builder.source,
                builder.externalId,
                builder.sourceFixtureId,
                builder.competition,
                builder.season,
                builder.groupNumber,
                builder.round,
                builder.phase,
                builder.dateTime,
                builder.city,
                builder.venue,
                builder.homeTeam,
                builder.awayTeam,
                builder.winnerTeam,
                builder.refereeName,
                builder.refereeLicense,
                builder.homeGamesWon,
                builder.awayGamesWon,
                builder.homeSetsWon,
                builder.awaySetsWon,
                builder.protested,
                builder.status,
                builder.sourceChecksum
        );
    }
    private static Match createNew(MatchBuilder matchBuilder) {
        Match match = of(matchBuilder);
        match.publishMatchCreatedEvent();
        return match;
    }

    private static Match createExisting(MatchBuilder matchBuilder) {
        return of(matchBuilder);
    }

    /**
     * A copy of this match with only {@code sourceFixtureId} changed (FEAT-00083). Used by the
     * lifecycle writer and in-memory repositories so header copies never drop the field by
     * rebuilding it field by field. No event is published.
     */
    public Match withSourceFixtureId(String sourceFixtureId) {
        return Match.builder()
                .id(id)
                .source(source)
                .externalId(externalId)
                .sourceFixtureId(sourceFixtureId)
                .competition(competition)
                .season(season)
                .groupNumber(groupNumber)
                .round(round)
                .phase(phase)
                .dateTime(dateTime)
                .city(city)
                .venue(venue)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .winnerTeam(winnerTeam)
                .refereeName(refereeName)
                .refereeLicense(refereeLicense)
                .homeGamesWon(homeGamesWon)
                .awayGamesWon(awayGamesWon)
                .homeSetsWon(homeSetsWon)
                .awaySetsWon(awaySetsWon)
                .protested(protested)
                .status(status)
                .sourceChecksum(sourceChecksum)
                .createExisting();
    }

    /**
     * A copy of this match with only {@code sourceChecksum} changed (FEAT-00089). Used by the
     * lifecycle writer and in-memory repositories so a checksum can be adopted or recorded without
     * rebuilding the header field by field. No event is published. The match id and natural key are
     * unchanged.
     */
    public Match withSourceChecksum(String sourceChecksum) {
        return Match.builder()
                .id(id)
                .source(source)
                .externalId(externalId)
                .sourceFixtureId(sourceFixtureId)
                .competition(competition)
                .season(season)
                .groupNumber(groupNumber)
                .round(round)
                .phase(phase)
                .dateTime(dateTime)
                .city(city)
                .venue(venue)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .winnerTeam(winnerTeam)
                .refereeName(refereeName)
                .refereeLicense(refereeLicense)
                .homeGamesWon(homeGamesWon)
                .awayGamesWon(awayGamesWon)
                .homeSetsWon(homeSetsWon)
                .awaySetsWon(awaySetsWon)
                .protested(protested)
                .status(status)
                .sourceChecksum(sourceChecksum)
                .createExisting();
    }

    public void delete() {
        publishMatchDeletedEvent();
    }

    private void publishMatchCreatedEvent() {
        publishEvent(MatchCreatedEvent.of(this.id));
    }

    private void publishMatchDeletedEvent() {
        publishEvent(MatchDeletedEvent.of(this.id));
    }

    public static final class MatchBuilder {
        private UUID id;
        private ImportSource source = ImportSource.RFETM;
        private String externalId;
        private String sourceFixtureId;
        private String competition;
        private Season season;
        private Integer groupNumber;
        private int round;
        private String phase;
        private ZonedDateTime dateTime;
        private String city;
        private String venue;
        private Team homeTeam;
        private Team awayTeam;
        private Team winnerTeam;
        private String refereeName;
        private String refereeLicense;
        private Integer homeGamesWon;
        private Integer awayGamesWon;
        private Integer homeSetsWon;
        private Integer awaySetsWon;
        private boolean protested;
        private MatchStatus status = MatchStatus.PLAYED;
        private String sourceChecksum;

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

        public MatchBuilder sourceFixtureId(String sourceFixtureId) {
            this.sourceFixtureId = sourceFixtureId;
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

        public MatchBuilder groupNumber(Integer groupNumber) {
            this.groupNumber = groupNumber;
            return this;
        }

        public MatchBuilder round(int round) {
            this.round = round;
            return this;
        }

        public MatchBuilder phase(String phase) {
            this.phase = phase;
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

        public MatchBuilder homeTeam(Team homeTeam) {
            this.homeTeam = homeTeam;
            return this;
        }

        public MatchBuilder awayTeam(Team awayTeam) {
            this.awayTeam = awayTeam;
            return this;
        }

        public MatchBuilder refereeName(String refereeName) {
            this.refereeName = refereeName;
            return this;
        }

        public MatchBuilder refereeLicense(String refereeLicense) {
            this.refereeLicense = refereeLicense;
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

        public MatchBuilder winnerTeam(Team winnerTeam) {
            this.winnerTeam = winnerTeam;
            return this;
        }

        public MatchBuilder status(MatchStatus status) {
            this.status = status;
            return this;
        }

        public MatchBuilder sourceChecksum(String sourceChecksum) {
            this.sourceChecksum = sourceChecksum;
            return this;
        }

        public Match createNew() {
            return Match.createNew(this);
        }

        public Match createExisting() {
            return Match.createExisting(this);
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

    /**
     * The source-supplied fixture id ({@code id_partido}) captured at import time (FEAT-00083),
     * or {@code null} for legacy rows and un-published BCNESA split fixtures. Stored verbatim and
     * treated as an opaque per-source key; never derived from file names.
     */
    public String getSourceFixtureId() {
        return sourceFixtureId;
    }

    public String getCompetition() {
        return competition;
    }

    public Season getSeason() {
        return season;
    }

    public Integer getGroupNumber() {
        return groupNumber;
    }

    public int getRound() {
        return round;
    }

    public String getPhase() {
        return phase;
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

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public String getRefereeName() {
        return refereeName;
    }

    public String getRefereeLicense() {
        return refereeLicense;
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

    public Team getWinnerTeam() {
        return winnerTeam;
    }

    public MatchStatus getStatus() {
        return status;
    }

    /**
     * The checksum of the acta content last applied to this match (FEAT-00089), or {@code null} for
     * legacy rows and matches with no stored checksum. Never set on a SCHEDULED match.
     */
    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public boolean isPlayed() {
        return status == MatchStatus.PLAYED;
    }

    /**
     * Whether {@code other} is the same fixture: identical source, competition, season, group
     * number, round, phase and team ids (FEAT-00080). Results, schedules and children may differ;
     * a replacement that changes this key would silently move a fixture and must be rejected.
     */
    public boolean hasSameNaturalKeyAs(Match other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(source, other.source)
                && Objects.equals(competition, other.competition)
                && Objects.equals(season, other.season)
                && Objects.equals(groupNumber, other.groupNumber)
                && round == other.round
                && Objects.equals(phase, other.phase)
                && Objects.equals(teamId(homeTeam), teamId(other.homeTeam))
                && Objects.equals(teamId(awayTeam), teamId(other.awayTeam));
    }

    private static java.util.UUID teamId(Team team) {
        return team == null ? null : team.getId();
    }
}
