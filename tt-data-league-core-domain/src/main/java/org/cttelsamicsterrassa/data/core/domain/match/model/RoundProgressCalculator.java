package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The single place the jornada-progress rule lives (FEAT-00084). Repositories — JPA or in-memory —
 * only produce grouped {@link RoundStatusCount} rows; this class turns them into
 * {@link RoundProgress} values so the definition can never drift between adapters or tests.
 *
 * <p>Rows are grouped by {@code (competition, groupNumber, phase)} with null-safe equality, because
 * that is the match natural key minus the teams: some sources reuse round numbers across phases, and
 * a group or phase can be absent. Within one group:</p>
 * <ul>
 *   <li>{@code currentRound} is the highest round holding at least one {@link MatchStatus#PLAYED}
 *       match, or {@code null} when nothing has been played;</li>
 *   <li>{@code lastCompleteRound} is the highest stored round with no {@link MatchStatus#SCHEDULED}
 *       match at or below it, or {@code null} when the lowest stored round is still pending. Only
 *       stored rounds count: a gap in round numbers is not a pending match, and no total number of
 *       rounds is inferred because a source export is a sliding window;</li>
 *   <li>{@code scheduledMatches} and {@code playedMatches} are the per-status sums.</li>
 * </ul>
 *
 * <p>Results are sorted by competition, group number and phase (nulls last on each), so the output is
 * deterministic.</p>
 */
public final class RoundProgressCalculator {

    private RoundProgressCalculator() {
    }

    public static List<RoundProgress> compute(ImportSource source, Season season,
                                              Collection<RoundStatusCount> counts) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        if (counts == null || counts.isEmpty()) {
            return List.of();
        }

        Map<RoundKey, List<RoundStatusCount>> rowsByKey = new LinkedHashMap<>();
        for (RoundStatusCount count : counts) {
            Objects.requireNonNull(count, "count");
            rowsByKey.computeIfAbsent(new RoundKey(count.competition(), count.groupNumber(), count.phase()),
                    key -> new ArrayList<>()).add(count);
        }

        List<RoundProgress> progress = new ArrayList<>(rowsByKey.size());
        for (Map.Entry<RoundKey, List<RoundStatusCount>> entry : rowsByKey.entrySet()) {
            RoundKey key = entry.getKey();
            List<RoundStatusCount> rows = entry.getValue();
            progress.add(new RoundProgress(source, season, key.competition(), key.groupNumber(), key.phase(),
                    currentRound(rows), lastCompleteRound(rows),
                    sumByStatus(rows, MatchStatus.SCHEDULED), sumByStatus(rows, MatchStatus.PLAYED)));
        }

        progress.sort(Comparator
                .comparing(RoundProgress::competition, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RoundProgress::groupNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RoundProgress::phase, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(progress);
    }

    private static Integer currentRound(List<RoundStatusCount> rows) {
        return rows.stream()
                .filter(row -> row.status() == MatchStatus.PLAYED)
                .map(RoundStatusCount::round)
                .max(Integer::compare)
                .orElse(null);
    }

    private static Integer lastCompleteRound(List<RoundStatusCount> rows) {
        Integer firstPendingRound = rows.stream()
                .filter(row -> row.status() == MatchStatus.SCHEDULED)
                .map(RoundStatusCount::round)
                .min(Integer::compare)
                .orElse(null);
        return rows.stream()
                .map(RoundStatusCount::round)
                .filter(round -> firstPendingRound == null || round < firstPendingRound)
                .max(Integer::compare)
                .orElse(null);
    }

    private static long sumByStatus(List<RoundStatusCount> rows, MatchStatus status) {
        return rows.stream().filter(row -> row.status() == status).mapToLong(RoundStatusCount::matches).sum();
    }

    private record RoundKey(String competition, Integer groupNumber, String phase) {
    }
}
