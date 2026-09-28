package org.cttelsamicsterrassa.data.core.application.importresource.shared.dto;

import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;

import java.util.List;

/**
 * Maps domain {@link RoundProgress} rows to {@link RoundProgressDto}s, dropping the source and season
 * that the enclosing DTO already states (FEAT-00084). Shared by the import-run result and the
 * import-resource read model so both expose the same shape.
 */
public final class RoundProgressDtoMapper {

    private RoundProgressDtoMapper() {
    }

    public static List<RoundProgressDto> toDtos(List<RoundProgress> progress) {
        if (progress == null || progress.isEmpty()) {
            return List.of();
        }
        return progress.stream()
                .map(row -> new RoundProgressDto(row.competition(), row.groupNumber(), row.phase(),
                        row.currentRound(), row.lastCompleteRound(), row.scheduledMatches(), row.playedMatches()))
                .toList();
    }
}
