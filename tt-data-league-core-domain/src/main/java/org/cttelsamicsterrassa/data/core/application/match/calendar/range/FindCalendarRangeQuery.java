package org.cttelsamicsterrassa.data.core.application.match.calendar.range;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Reads the matches of every competition of a source and season dated in {@code [from, to)}
 * (FEAT-00093). {@code competition}, {@code groupNumber} and {@code teamId} are optional filters.
 */
public class FindCalendarRangeQuery extends DomainQuery {

    /** Widest accepted range: covers a 6-week month grid. */
    public static final int MAX_RANGE_DAYS = 62;

    private final ImportSource source;
    private final Season season;
    private final LocalDate from;
    private final LocalDate to;
    private final String competition;
    private final Integer groupNumber;
    private final UUID teamId;

    public FindCalendarRangeQuery(ImportSource source, Season season, LocalDate from, LocalDate to,
                                  String competition, Integer groupNumber, UUID teamId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        if (source == null || season == null || from == null || to == null) {
            throw new IllegalArgumentException("source, season, from and to are mandatory");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        if (competition != null && competition.isBlank()) {
            throw new IllegalArgumentException("competition must not be blank");
        }
        if (groupNumber != null && competition == null) {
            throw new IllegalArgumentException("group requires a competition");
        }
        if (groupNumber != null && groupNumber < 1) {
            throw new IllegalArgumentException("group must be at least 1");
        }
        this.source = source;
        this.season = season;
        this.from = from;
        this.to = to;
        this.competition = competition;
        this.groupNumber = groupNumber;
        this.teamId = teamId;
    }

    public ImportSource getSource() {
        return source;
    }

    public Season getSeason() {
        return season;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    public String getCompetition() {
        return competition;
    }

    public Integer getGroupNumber() {
        return groupNumber;
    }

    public UUID getTeamId() {
        return teamId;
    }
}
