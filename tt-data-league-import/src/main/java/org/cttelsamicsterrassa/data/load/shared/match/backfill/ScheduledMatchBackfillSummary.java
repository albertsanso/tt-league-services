package org.cttelsamicsterrassa.data.load.shared.match.backfill;

import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.List;

/**
 * Result of one {@link ScheduledMatchBackfillService} run. In {@link ScheduledMatchBackfillMode#REPORT},
 * {@code written} carries an all-zero {@link ScheduledMatchBackfillWriteResult} because no write happened.
 */
public record ScheduledMatchBackfillSummary(
        ImportSource source,
        Season season,
        ScheduledMatchBackfillMode mode,
        List<ScheduledMatchBackfillCandidate> candidates,
        ScheduledMatchBackfillWriteResult written) {
}
