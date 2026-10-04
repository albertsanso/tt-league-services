package org.cttelsamicsterrassa.data.core.application.match.roundprogress;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Reads the per-jornada round progress of a source and season (FEAT-00102).
 *
 * <p>{@code source} and {@code season} are mandatory; {@code competition} is optional but not blank
 * when present. Filters never change a group header ({@code currentRound}/{@code lastCompleteRound}).</p>
 */
public class FindRoundProgressQuery extends DomainQuery {

    private final ImportSource source;
    private final Season season;
    private final String competition;
    private final boolean onlyOpen;

    public FindRoundProgressQuery(ImportSource source, Season season, String competition, boolean onlyOpen) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        if (source == null || season == null) {
            throw new IllegalArgumentException("source and season are mandatory");
        }
        if (competition != null && competition.isBlank()) {
            throw new IllegalArgumentException("competition must not be blank");
        }
        this.source = source;
        this.season = season;
        this.competition = competition;
        this.onlyOpen = onlyOpen;
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

    public boolean isOnlyOpen() {
        return onlyOpen;
    }
}
