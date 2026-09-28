package org.cttelsamicsterrassa.data.load.shared.execution;

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
     * reported issue carrying the processor name, the file and the classifier reason. These
     * records are informational only in this feature: feeding them into traversal summaries,
     * metrics or run status is FEAT-00082.
     */
    public void recordMatchOutcome(MatchLifecycleOutcome outcome, String processor, Path location, String reason) {
        Objects.requireNonNull(outcome, "outcome");
        matchOutcomeCounts.merge(outcome, 1, Integer::sum);
        if (outcome.isReportable()) {
            reportedMatchIssues.add(new ImportExecutionIssue(processor, String.valueOf(location), reason));
        }
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
