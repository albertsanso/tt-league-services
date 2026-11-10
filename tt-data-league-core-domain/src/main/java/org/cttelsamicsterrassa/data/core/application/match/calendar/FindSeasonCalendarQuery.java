package org.cttelsamicsterrassa.data.core.application.match.calendar;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Reads the season calendar for one competition of a source and season (FEAT-00092).
 *
 * <p>{@code source}, {@code season} and {@code competition} are mandatory; {@code groupNumber} and
 * {@code round} are optional narrow-down filters that never change a group's header counts. {@code region}
 * and {@code regionType} (FEAT-00121) restrict the matches read: a match without the attribute never
 * matches one.</p>
 */
public class FindSeasonCalendarQuery extends DomainQuery {

    private final ImportSource source;
    private final Season season;
    private final String competition;
    private final Integer groupNumber;
    private final Integer round;
    private final Region region;
    private final RegionType regionType;

    public FindSeasonCalendarQuery(ImportSource source, Season season, String competition,
                                   Integer groupNumber, Integer round) {
        this(source, season, competition, groupNumber, round, null, null);
    }

    public FindSeasonCalendarQuery(ImportSource source, Season season, String competition,
                                   Integer groupNumber, Integer round, Region region, RegionType regionType) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        if (source == null || season == null) {
            throw new IllegalArgumentException("source and season are mandatory");
        }
        if (competition == null || competition.isBlank()) {
            throw new IllegalArgumentException("competition is mandatory");
        }
        if (groupNumber != null && groupNumber < 1) {
            throw new IllegalArgumentException("group must be at least 1");
        }
        if (round != null && round < 1) {
            throw new IllegalArgumentException("round must be at least 1");
        }
        this.source = source;
        this.season = season;
        this.competition = competition;
        this.groupNumber = groupNumber;
        this.round = round;
        this.region = region;
        this.regionType = regionType;
    }

    public ImportSource getSource() {
        return source;
    }

    public Season getSeason() {
        return season;
    }

    public String getCompetition() {
        return competition;
    }

    public Integer getGroupNumber() {
        return groupNumber;
    }

    public Integer getRound() {
        return round;
    }

    public Region getRegion() {
        return region;
    }

    public RegionType getRegionType() {
        return regionType;
    }
}