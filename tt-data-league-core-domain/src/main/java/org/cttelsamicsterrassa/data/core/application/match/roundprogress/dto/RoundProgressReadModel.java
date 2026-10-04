package org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.LocalDate;
import java.util.List;

/**
 * The per-jornada round progress of a source and season (FEAT-00102).
 */
public record RoundProgressReadModel(
        ImportSource source,
        Season season,
        String competition,
        boolean onlyOpen,
        LocalDate today,
        int overdueGraceDays,
        List<RoundProgressGroupReadModel> groups) {
}
