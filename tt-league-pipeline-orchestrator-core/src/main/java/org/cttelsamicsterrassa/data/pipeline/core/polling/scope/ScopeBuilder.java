package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;

/**
 * Joins the open tracker match days to the ingest status rows and groups the selected rows into poll units. An open
 * day without a status row fails the build ({@code SCOPE_UNMATCHED}); nothing is widened silently.
 */
public final class ScopeBuilder {

    private static final int MAX_LISTED_KEYS = 10;

    private final BcnesaCompetitionNames names;

    public ScopeBuilder(BcnesaCompetitionNames names) {
        this.names = Objects.requireNonNull(names, "names is required");
    }

    public ScopeBuild build(
            PipelineSource source, String season, List<OpenMatchDay> open, IngestMatchDayStatus status) {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(season, "season is required");
        Objects.requireNonNull(open, "open is required");
        Objects.requireNonNull(status, "status is required");
        SourceVocabulary vocabulary = SourceVocabulary.of(source, names);
        Map<PollUnit, UnitAccumulator> units = new LinkedHashMap<>();
        List<MatchDayKey> unmatched = new ArrayList<>();
        for (OpenMatchDay day : open) {
            List<IngestStatusRow> rows = status.rows().stream()
                    .filter(row -> row.season().equals(season) && vocabulary.matches(row, day.key()))
                    .toList();
            if (rows.isEmpty()) {
                unmatched.add(day.key());
                continue;
            }
            for (IngestStatusRow row : rows) {
                UnitAccumulator unit = units.computeIfAbsent(vocabulary.unit(row), key -> new UnitAccumulator());
                unit.matchDays.add(row.matchDay());
                day.candidates().forEach(match -> unit.candidates.putIfAbsent(match.matchId(), match));
            }
        }
        if (!unmatched.isEmpty()) {
            throw new ScopeBuildException(ScopeBuildException.SCOPE_UNMATCHED, unmatchedMessage(source, unmatched));
        }
        List<PollUnitScope> scopes = new ArrayList<>();
        units.forEach((unit, accumulator) -> scopes.add(new PollUnitScope(unit, unit.scopeKey(),
                unit.filter(List.copyOf(accumulator.matchDays)), List.copyOf(accumulator.candidates.values()))));
        return new ScopeBuild(scopes);
    }

    private static String unmatchedMessage(PipelineSource source, List<MatchDayKey> unmatched) {
        String listed = unmatched.stream().limit(MAX_LISTED_KEYS)
                .map(key -> key.competition() + " group " + key.groupNumber()
                        + (key.phase() == null ? "" : " phase " + key.phase()) + " round " + key.round())
                .collect(Collectors.joining("; "));
        int more = unmatched.size() - MAX_LISTED_KEYS;
        return unmatched.size() + " open match day(s) of " + source + " have no ingest status row: " + listed
                + (more > 0 ? "; and " + more + " more" : "");
    }

    private static final class UnitAccumulator {

        private final TreeSet<Integer> matchDays = new TreeSet<>();
        private final Map<UUID, MatchTracking> candidates = new LinkedHashMap<>();
    }
}
