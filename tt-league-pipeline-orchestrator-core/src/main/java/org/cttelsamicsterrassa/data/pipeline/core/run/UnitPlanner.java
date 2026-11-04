package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The only place that decides how a run splits into units. Pure: no I/O and no clock. A full-season scope is one
 * {@code season} unit; otherwise there is one unit per distinct ingest-group identity, merging the match days of the
 * filters that share one (the same rule as the poll scope builder), in first-seen order.
 */
public final class UnitPlanner {

    public static final String SEASON_LABEL = "Full season";
    public static final String LEGACY_LABEL = "Legacy scope";

    private UnitPlanner() {
    }

    public static List<RunUnit> plan(UUID runId, RunScope scope) {
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(scope, "scope is required");
        if (scope.isFullSeason()) {
            return List.of(RunUnit.plan(UUID.randomUUID(), runId, 0, UnitKey.SEASON, SEASON_LABEL, scope));
        }
        Map<String, Merged> byKey = new LinkedHashMap<>();
        for (ScopeFilter filter : scope.filters()) {
            byKey.computeIfAbsent(UnitKey.of(filter), key -> new Merged(filter)).add(filter);
        }
        List<RunUnit> units = new ArrayList<>();
        byKey.forEach((key, merged) -> {
            ScopeFilter filter = merged.filter();
            units.add(RunUnit.plan(UUID.randomUUID(), runId, units.size(), key, label(filter),
                    new RunScope(List.of(filter))));
        });
        return List.copyOf(units);
    }

    /**
     * Copies the given units of an original run into a new run for a replay: same ordinal, key, label and scope, new
     * ids, every unit pending again. The caller chooses which originals qualify.
     */
    public static List<RunUnit> replan(UUID newRunId, List<RunUnit> originals) {
        Objects.requireNonNull(newRunId, "newRunId is required");
        Objects.requireNonNull(originals, "originals is required");
        return originals.stream()
                .map(original -> RunUnit.plan(UUID.randomUUID(), newRunId, original.ordinal(), original.unitKey(),
                        original.label(), original.scope()))
                .toList();
    }

    /** {@code territory category gender group phase · J3,J4}: the set fields in that order, then the match days. */
    static String label(ScopeFilter filter) {
        String identity = Stream.of(
                        filter.territory(), filter.category(), filter.gender(), filter.group(), filter.phase())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        String days = filter.matchDays().stream().map(day -> "J" + day).collect(Collectors.joining(","));
        String label;
        if (identity.isEmpty()) {
            label = days;
        } else if (days.isEmpty()) {
            label = identity;
        } else {
            label = identity + " · " + days;
        }
        return label.length() <= RunUnit.MAX_LABEL ? label : label.substring(0, RunUnit.MAX_LABEL - 1) + "…";
    }

    /** The filters of one identity; an empty match-day list means every match day and wins over a listed subset. */
    private static final class Merged {

        private final ScopeFilter first;
        private final TreeSet<Integer> matchDays = new TreeSet<>();
        private boolean allMatchDays;

        private Merged(ScopeFilter first) {
            this.first = first;
        }

        private void add(ScopeFilter filter) {
            if (filter.matchDays().isEmpty()) {
                allMatchDays = true;
            } else {
                matchDays.addAll(filter.matchDays());
            }
        }

        private ScopeFilter filter() {
            return new ScopeFilter(first.category(), first.group(), first.phase(), first.territory(), first.gender(),
                    allMatchDays ? List.of() : List.copyOf(matchDays));
        }
    }
}
