package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable state owned by one import invocation. It is deliberately not static or shared between
 * requests; processors may use it for exact-key lookup memoization as the execution pipeline grows.
 */
public final class ImportRunContext {
    private final ImportSource source;
    private final String season;
    private final AmendedActaMode amendedActaMode;
    private final Map<Object, Object> lookups = new HashMap<>();
    private final Map<MatchLifecycleOutcome, Integer> matchOutcomeCounts =
            new EnumMap<>(MatchLifecycleOutcome.class);
    private final List<ImportExecutionIssue> reportedMatchIssues = new ArrayList<>();
    private long unresolvedPendingFixtureCount;
    private final Set<String> snapshotFixtureIds = new LinkedHashSet<>();
    private final Set<SnapshotFixtures.NaturalKey> snapshotNaturalKeys = new LinkedHashSet<>();
    private final Map<SnapshotFixtures.Scope, Integer> snapshotHighestRoundByScope = new HashMap<>();

    public ImportRunContext(ImportSource source, String season) {
        this(source, season, null);
    }

    /**
     * @param amendedActaMode the amended-acta detection mode (FEAT-00089), or {@code null} when
     *                        detection is disabled (the default)
     */
    public ImportRunContext(ImportSource source, String season, AmendedActaMode amendedActaMode) {
        this.source = Objects.requireNonNull(source, "source");
        this.season = season;
        this.amendedActaMode = amendedActaMode;
    }

    public ImportSource source() {
        return source;
    }

    public String season() {
        return season;
    }

    /** The amended-acta detection mode for this run (FEAT-00089); {@code null} when disabled. */
    public AmendedActaMode amendedActaMode() {
        return amendedActaMode;
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
     * Records one fixture seen by the snapshot run (FEAT-00086). The processors call this before
     * team resolution, the identity guard and the writer, so fixtures with an unregistered team and
     * {@code FIXTURE_IDENTITY_CONFLICT} fixtures are still "seen" and are not falsely reported as
     * absent. It accumulates the non-null fixture id, the natural key when both team ids are present,
     * and the highest round per competition/group/phase scope. It never touches the outcome counters,
     * the lifecycle counters or the reported issues.
     *
     * @param competition    required
     * @param sourceFixtureId nullable
     * @param homeTeamId     nullable, but both team ids are present or both are absent
     * @param awayTeamId     nullable, but both team ids are present or both are absent
     * @throws NullPointerException     if {@code competition} is {@code null}
     * @throws IllegalArgumentException if exactly one of the two team ids is {@code null}
     */
    public void recordSnapshotFixture(String competition, Integer groupNumber, String phase, int round,
                                      String sourceFixtureId, UUID homeTeamId, UUID awayTeamId) {
        Objects.requireNonNull(competition, "competition");
        if ((homeTeamId == null) != (awayTeamId == null)) {
            throw new IllegalArgumentException(
                    "homeTeamId and awayTeamId must be both present or both absent");
        }
        if (sourceFixtureId != null) {
            snapshotFixtureIds.add(sourceFixtureId);
        }
        if (homeTeamId != null) {
            snapshotNaturalKeys.add(new SnapshotFixtures.NaturalKey(competition, groupNumber, phase, round,
                    homeTeamId, awayTeamId));
        }
        snapshotHighestRoundByScope.merge(new SnapshotFixtures.Scope(competition, groupNumber, phase),
                round, Math::max);
    }

    /** The fixtures seen so far, as an immutable value (FEAT-00086). */
    public SnapshotFixtures snapshotFixtures() {
        return new SnapshotFixtures(snapshotFixtureIds, snapshotNaturalKeys, snapshotHighestRoundByScope);
    }

    /**
     * The run's lifecycle counters (FEAT-00082): the per-acta exclusive match outcomes recorded by
     * {@link #recordMatchOutcome} plus the unresolved pending fixtures recorded by
     * {@link #recordUnresolvedPendingFixture}. {@code amendedPlayed} counts {@code PLAYED_AMENDED} only: the
     * report-mode {@code PLAYED_AMENDMENT_REPORTED} wrote nothing and is not counted.
     */
    public ImportLifecycleCounters lifecycleCounters() {
        return new ImportLifecycleCounters(
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.SCHEDULED_CREATED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.UPGRADED_TO_PLAYED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.RESCHEDULED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.PARTIAL_REPORTED, 0),
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.INVALID_REPORTED, 0),
                unresolvedPendingFixtureCount,
                matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.PLAYED_AMENDED, 0));
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
