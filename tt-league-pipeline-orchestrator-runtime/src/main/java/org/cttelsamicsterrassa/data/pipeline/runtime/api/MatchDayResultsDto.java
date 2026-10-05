package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Results of a match day's matches, read from the platform on demand; never stored by the orchestrator. */
public record MatchDayResultsDto(UUID matchDayId, LocalDate platformToday, List<MatchResultDto> results) {
}
