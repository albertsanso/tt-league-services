package org.cttelsamicsterrassa.data.pipeline.core.statistics.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StepFacts;

/**
 * Read-only access to the facts the statistics are computed from. Every method is source-filtered, and an empty set
 * means all sources. Ranges are half-open: {@code from} inclusive, {@code to} exclusive.
 */
public interface StatisticsReadRepository {

    /** Terminal runs with {@code finishedAt} in the range. */
    List<RunFacts> terminalRunsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources);

    /** Finished step attempts (succeeded or failed) with {@code finishedAt} in the range. */
    List<StepFacts> stepsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources);

    /** Tracked matches of the season; a null season means every season. */
    List<MatchFacts> matchesBySeason(Set<PipelineSource> sources, String season);

    /**
     * A prefilter for one day, of every source: matches with {@code reportedAt} in {@code [start, end)}, or dated
     * before {@code end}, not ignored and not reported before {@code end}. The core applies the rules again.
     */
    List<MatchFacts> matchesForDay(Instant start, Instant end);

    List<CorrectionFacts> importReportsReceivedBetween(Instant from, Instant to, Set<PipelineSource> sources);
}
