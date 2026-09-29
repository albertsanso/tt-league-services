package org.cttelsamicsterrassa.data.core.application.match.calendar;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Reads the season calendar for one competition of a source and season (FEAT-00092).
 *
 * <p>{@code source}, {@code season} and {@code competition} are mandatory; {@code groupNumber} and
 * {@code round} are optional narrow-down filters that never change a group's header counts.</p>
 */
public class FindSeasonCalendarQuery extends DomainQuery {

    private final ImportSource source;
    private final Season season;
    private final String competition;
    private final Integer groupNumber;
    private final Integer round;

    public FindSeasonCalendarQuery(ImportSource source, Season season, String competition,
                                   Integer groupNumber, Integer round) {
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
}