package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable state owned by one import invocation. It is deliberately not static or shared between
 * requests; processors may use it for exact-key lookup memoization as the execution pipeline grows.
 */
public final class ImportRunContext {
    private final ImportSource source;
    private final String season;
    private final Map<Object, Object> lookups = new HashMap<>();
    private final Map<MatchLifecycleOutcome, Integer> matchOutcomeCounts =
            new EnumMap<>(MatchLifecycleOutcome.class);
    private final List<ImportExecutionIssue> reportedMatchIssues = new ArrayList<>();
    private long unresolvedPendingFixtureCount;

    public ImportRunContext(ImportSource source, String season) {
        this.source = Objects.requireNonNull(source, "source");
        this.season = season;
    }

    public ImportSource source() {
        return source;
    }

    public String season() {
        return season;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        return (T) lookups.get(key);
    }

    public void put(Object key, Object value) {
        lookups.put(Objects.requireNonNull(key, "key"), value);
    }

    public int size() {
        return lookups.size();
    }

    /**
     * Records one match-lifecycle outcome (FEAT-00081). Reportable outcomes additionally become a
     * reported issue carrying the processor name, the file and the classifier reason. Since
     * FEAT-00082 these records feed the traversal summaries, metrics and run status through
     * {@link #lifecycleCounters()} and the warnings channel.
     */
    public void recordMatchOutcome(MatchLifecycleOutcome outcome, String processor, Path location, String reason) {
        Objects.requireNonNull(outcome, "outcome");
        matchOutcomeCounts.merge(outcome, 1, Integer::sum);
        if (outcome.isReportable()) {
            reportedMatchIssues.add(new ImportExecutionIssue(processor, String.valueOf(location), reason));
        }
    }

    /**
     * Reports that a processor had to fall back to a contextual round because the payload carried
     * none (FEAT-00085, gap G8). Like the reported match outcomes this is a warning only: it never
     * touches the outcome counters, the lifecycle counters or the run status.
     */
    public void recordRoundFallback(String processor, Path location, String reason) {
        Objects.requireNonNull(processor, "processor");
        Objects.requireNonNull(reason, "reason");
        reportedMatchIssues.add(new ImportExecutionIssue(processor, String.valueOf(location), reason));
    }

    /**
     * Records one unresolved pending fixture (FEAT-00082): a pending acta whose teams could not be
     * attributed, so it was skipped instead of dispatched. It increments the lifecycle counter and
     * becomes a reported issue carrying the navigator name, the location and the classifier reason.
     */
    public void recordUnresolvedPendingFixture(String navigator, Path location, String reason) {
        unresolvedPendingFixtureCount++;
        reportedMatchIssues.add(new ImportExecutionIssue(navigator, String.valueOf(location), reason));
    }

    /**
     * The run's lifecycle counters (FEAT-00082): the per-acta exclusive match outcomes recorded by
     * {@link #recordMatchOutcome} plus the unresolved pending fixtures recorded by
     * {@link #recordUnresolvedPendingFixture}.
     */
    public ImportLifecycleCounters lifecycleCounters() {
        return new ImportLifecycleCounters(
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.SCHEDULED_CREATED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.UPGRADED_TO_PLAYED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.RESCHEDULED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.PARTIAL_REPORTED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.INVALID_REPORTED, 0),
                unresolvedPendingFixtureCount);
    }

    /** Outcome counters accumulated so far, as an unmodifiable snapshot. */
    public Map<MatchLifecycleOutcome, Integer> matchOutcomeCounts() {
        return Collections.unmodifiableMap(new EnumMap<>(matchOutcomeCounts));
    }

    /** Reported match-lifecycle issues accumulated so far, as an unmodifiable snapshot. */
    public List<ImportExecutionIssue> reportedMatchIssues() {
        return List.copyOf(reportedMatchIssues);
    }
}
