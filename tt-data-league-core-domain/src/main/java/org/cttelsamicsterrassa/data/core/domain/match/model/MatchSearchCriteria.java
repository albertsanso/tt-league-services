package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.LocalDate;
import java.util.UUID;

public record MatchSearchCriteria(
        ImportSource source,
        Season season,
        String competition,
        LocalDate fromDate,
        LocalDate toDate,
        UUID playerId,
        PlayerLocation playerLocation,
        String playerName,
        String clubName,
        int page,
        int pageSize) {

    public MatchSearchCriteria {
        if (source == null || season == null) {
            throw new IllegalArgumentException("source and season are mandatory");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page must be non-negative and pageSize must be between 1 and 100");
        }
        competition = competition == null || competition.isBlank() ? null : competition.trim();
        playerName = playerName == null || playerName.isBlank() ? null : playerName.trim();
        clubName = clubName == null || clubName.isBlank() ? null : clubName.trim();
    }

    public MatchSearchCriteria(
            ImportSource source,
            Season season,
            String competition,
            LocalDate fromDate,
            LocalDate toDate,
            UUID playerId,
            PlayerLocation playerLocation,
            String playerName) {
        this(source, season, competition, fromDate, toDate, playerId, playerLocation, playerName, null, 0, 10);
    }
}
