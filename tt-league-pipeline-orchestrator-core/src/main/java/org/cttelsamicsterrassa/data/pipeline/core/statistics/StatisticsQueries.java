package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.DailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.StatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;

/**
 * Read-only statistics, one method per API endpoint. Methods taking a {@link DateRange} read with the settings zone.
 * Nothing is cached and nothing is written; every figure comes from {@link StatisticsRules}. An empty source set
 * means all sources.
 */
public final class StatisticsQueries {

    private final StatisticsReadRepository reads;
    private final DailyStatsRepository daily;
    private final MatchDayRepository matchDays;
    private final RunClock clock;
    private final StatisticsSettings settings;

    public StatisticsQueries(
            StatisticsReadRepository reads,
            DailyStatsRepository daily,
            MatchDayRepository matchDays,
            RunClock clock,
            StatisticsSettings settings) {
        this.reads = Objects.requireNonNull(reads, "reads is required");
        this.daily = Objects.requireNonNull(daily, "daily is required");
        this.matchDays = Objects.requireNonNull(matchDays, "matchDays is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.settings = Objects.requireNonNull(settings, "settings is required");
    }

    public ZoneId zone() {
        return settings.zone();
    }

    public List<DailyStats> daily(DateRange range, Set<PipelineSource> sources) {
        return daily.find(range, sources);
    }

    public RunOutcomeStats runs(DateRange range, Set<PipelineSource> sources) {
        return runs(range, sources, Optional.empty());
    }

    /**
     * Run outcomes and step averages; a present unit key keeps only the runs that have a finished unit with that key
     * and only the steps that ran for it.
     */
    public RunOutcomeStats runs(DateRange range, Set<PipelineSource> sources, Optional<String> unitKey) {
        ZoneId zone = settings.zone();
        Instant from = range.startInstant(zone);
        Instant to = range.endInstant(zone);
        Set<UUID> runsWithUnit = unitKey.isEmpty()
                ? null
                : reads.unitsFinishedBetween(from, to, sources, unitKey).stream()
                        .map(UnitFacts::runId)
                        .collect(Collectors.toSet());
        Map<DayKey, int[]> counts = new TreeMap<>();
        for (RunFacts run : reads.terminalRunsFinishedBetween(from, to, sources)) {
            if (runsWithUnit != null && !runsWithUnit.contains(run.runId())) {
                continue;
            }
            int[] row = counts.computeIfAbsent(
                    new DayKey(LocalDate.ofInstant(run.finishedAt(), zone), run.source()), key -> new int[4]);
            switch (run.status()) {
                case SUCCEEDED -> row[0]++;
                case NO_CHANGES -> row[1]++;
                case PARTIAL -> row[2]++;
                case FAILED -> row[3]++;
                default -> throw new IllegalStateException("not a terminal status: " + run.status());
            }
        }
        List<RunOutcomeStats.DayOutcomes> days = counts.entrySet().stream()
                .map(e -> new RunOutcomeStats.DayOutcomes(
                        e.getKey().date(), e.getKey().source(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        e.getValue()[3]))
                .toList();
        Map<SourceKind, List<Duration>> durations = new TreeMap<>();
        for (StepFacts step : reads.stepsFinishedBetween(from, to, sources)) {
            if (unitKey.isPresent() && !unitKey.get().equals(step.unitKey())) {
                continue;
            }
            durations
                    .computeIfAbsent(new SourceKind(step.source(), step.kind()), key -> new ArrayList<>())
                    .add(Duration.between(step.startedAt(), step.finishedAt()));
        }
        List<RunOutcomeStats.StepAverage> averages = durations.entrySet().stream()
                .map(e -> new RunOutcomeStats.StepAverage(
                        e.getKey().source(),
                        e.getKey().kind(),
                        e.getValue().size(),
                        StatisticsRules.mean(e.getValue())))
                .toList();
        return new RunOutcomeStats(days, averages);
    }

    /** Terminal units by outcome per source and unit key, optionally limited to one unit key. */
    public UnitOutcomeStats units(DateRange range, Set<PipelineSource> sources, Optional<String> unitKey) {
        ZoneId zone = settings.zone();
        return new UnitOutcomeStats(StatisticsRules.unitOutcomes(
                reads.unitsFinishedBetween(range.startInstant(zone), range.endInstant(zone), sources, unitKey)));
    }

    public TimeToReportStats timeToReport(String season, Set<PipelineSource> sources) {
        PipelineRun.requireValidSeason(season);
        Map<PipelineSource, List<Duration>> perSource = new EnumMap<>(PipelineSource.class);
        Map<SourceCompetition, List<Duration>> perCompetition = new TreeMap<>();
        for (MatchFacts match : reads.matchesBySeason(sources, season)) {
            Optional<Duration> time = StatisticsRules.timeToReport(match);
            if (time.isEmpty()) {
                continue;
            }
            perSource.computeIfAbsent(match.source(), key -> new ArrayList<>()).add(time.get());
            perCompetition
                    .computeIfAbsent(new SourceCompetition(match.source(), match.competition()), key -> new ArrayList<>())
                    .add(time.get());
        }
        List<TimeToReportStats.Row> rows = new ArrayList<>();
        for (PipelineSource source : PipelineSource.values()) {
            List<Duration> total = perSource.get(source);
            if (total == null) {
                continue;
            }
            rows.add(row(source, null, total));
            perCompetition.forEach((key, values) -> {
                if (key.source() == source) {
                    rows.add(row(source, key.competition(), values));
                }
            });
        }
        return new TimeToReportStats(rows);
    }

    private static TimeToReportStats.Row row(PipelineSource source, String competition, List<Duration> values) {
        List<Duration> sorted = values.stream().sorted().toList();
        return new TimeToReportStats.Row(
                source,
                competition,
                sorted.size(),
                StatisticsRules.percentile(sorted, 0.5),
                StatisticsRules.percentile(sorted, 0.9));
    }

    /** Pending matches by age; a null season means every season. */
    public PendingByAge pending(Set<PipelineSource> sources, String season) {
        if (season != null) {
            PipelineRun.requireValidSeason(season);
        }
        Instant now = clock.now();
        Map<PipelineSource, int[]> counts = new EnumMap<>(PipelineSource.class);
        for (MatchFacts match : reads.matchesBySeason(sources, season)) {
            if (!StatisticsRules.isPendingNow(match, now)) {
                continue;
            }
            int[] row = counts.computeIfAbsent(match.source(), key -> new int[5]);
            row[StatisticsRules.ageBucket(match.matchDateTime(), now).ordinal()]++;
            if (match.status() == TrackedMatchStatus.OVERDUE) {
                row[4]++;
            }
        }
        List<PendingByAge.SourcePending> rows = new ArrayList<>();
        for (PipelineSource source : PipelineSource.values()) {
            if (!sources.isEmpty() && !sources.contains(source)) {
                continue;
            }
            int[] row = counts.getOrDefault(source, new int[5]);
            rows.add(new PendingByAge.SourcePending(source, row[0], row[1], row[2], row[3], row[4]));
        }
        return new PendingByAge(now, rows);
    }

    public CorrectionStats corrections(DateRange range, Set<PipelineSource> sources) {
        ZoneId zone = settings.zone();
        Map<DayKey, Long> perDay = new TreeMap<>();
        Map<PipelineSource, Long> totals = new EnumMap<>(PipelineSource.class);
        for (CorrectionFacts fact :
                reads.importReportsReceivedBetween(range.startInstant(zone), range.endInstant(zone), sources)) {
            perDay.merge(new DayKey(LocalDate.ofInstant(fact.receivedAt(), zone), fact.source()),
                    fact.amendedPlayed(), Long::sum);
            totals.merge(fact.source(), fact.amendedPlayed(), Long::sum);
        }
        return new CorrectionStats(
                perDay.entrySet().stream()
                        .map(e -> new CorrectionStats.DayCorrections(
                                e.getKey().date(), e.getKey().source(), e.getValue()))
                        .toList(),
                totals.entrySet().stream()
                        .map(e -> new CorrectionStats.SourceCorrections(e.getKey(), e.getValue()))
                        .toList());
    }

    public SourceHealthStats sourceHealth(DateRange range, Set<PipelineSource> sources) {
        ZoneId zone = settings.zone();
        Map<DayKey, long[]> perDay = new TreeMap<>();
        Map<PipelineSource, long[]> totals = new EnumMap<>(PipelineSource.class);
        for (StepFacts step : reads.stepsFinishedBetween(range.startInstant(zone), range.endInstant(zone), sources)) {
            if (step.kind() != StepKind.INGEST) {
                continue;
            }
            long[] day = perDay.computeIfAbsent(
                    new DayKey(LocalDate.ofInstant(step.finishedAt(), zone), step.source()), key -> new long[6]);
            long[] total = totals.computeIfAbsent(step.source(), key -> new long[6]);
            for (long[] row : List.of(day, total)) {
                IngestHealth health = step.health();
                if (health == null) {
                    row[5]++;
                } else {
                    row[0] += health.httpErrors();
                    row[1] += health.timeouts();
                    row[2] += health.parseErrors();
                }
                row[3]++;
                if ("SOURCE_UNAVAILABLE".equals(step.outcome())) {
                    row[4]++;
                }
            }
        }
        return new SourceHealthStats(
                perDay.entrySet().stream()
                        .map(e -> {
                            long[] v = e.getValue();
                            return new SourceHealthStats.DayHealth(
                                    e.getKey().date(), e.getKey().source(), v[0], v[1], v[2], (int) v[3], (int) v[4],
                                    (int) v[5]);
                        })
                        .toList(),
                totals.entrySet().stream()
                        .map(e -> {
                            long[] v = e.getValue();
                            return new SourceHealthStats.SourceHealth(
                                    e.getKey(), v[0], v[1], v[2], (int) v[3], (int) v[4], (int) v[5]);
                        })
                        .toList());
    }

    /** One row per dated match day of the source and season; a null competition means every competition. */
    public List<ReportingProgress> reportingProgress(PipelineSource source, String season, String competition) {
        Objects.requireNonNull(source, "source is required");
        PipelineRun.requireValidSeason(season);
        List<MatchDay> days = matchDays.findBySourceAndSeason(source, season).stream()
                .filter(day -> competition == null || day.key().competition().equals(competition))
                .filter(day -> day.window().isDated())
                .sorted(Comparator.comparing((MatchDay day) -> day.key().competition())
                        .thenComparing(day -> day.key().groupNumber(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(day -> day.key().phase(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingInt(day -> day.key().round()))
                .toList();
        Map<UUID, List<MatchTracking>> matchesByDay = matchDays
                .findMatches(days.stream().map(MatchDay::id).toList())
                .stream()
                .collect(Collectors.groupingBy(MatchTracking::matchDayId, HashMap::new, Collectors.toList()));
        LocalDate today = LocalDate.ofInstant(clock.now(), settings.zone());
        return days.stream()
                .map(day -> progress(day, matchesByDay.getOrDefault(day.id(), List.of()), today))
                .toList();
    }

    private ReportingProgress progress(MatchDay day, List<MatchTracking> matches, LocalDate today) {
        ZoneId zone = settings.zone();
        List<MatchTracking> active = matches.stream().filter(match -> !match.isIgnored()).toList();
        List<MatchTracking> reported = active.stream()
                .filter(match -> match.status() == TrackedMatchStatus.REPORTED && match.reportedAt() != null)
                .toList();
        int postponed = (int) active.stream()
                .filter(match -> match.status() == TrackedMatchStatus.POSTPONED)
                .count();
        LocalDate first = day.window().start();
        LocalDate last = day.window().end();
        LocalDate until = last.isBefore(today) ? last : today;
        List<ReportingProgress.Point> points = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(until); date = date.plusDays(1)) {
            Instant end = StatisticsRules.startOf(date.plusDays(1), zone);
            int reportedBy = (int) reported.stream()
                    .filter(match -> match.reportedAt().isBefore(end))
                    .count();
            points.add(new ReportingProgress.Point(date, reportedBy, active.size() - reportedBy - postponed));
        }
        return new ReportingProgress(
                day.id(),
                day.key(),
                day.state(),
                first,
                last,
                active.size(),
                reported.size(),
                postponed,
                active.size() - reported.size() - postponed,
                points);
    }

    private record DayKey(LocalDate date, PipelineSource source) implements Comparable<DayKey> {
        @Override
        public int compareTo(DayKey other) {
            int byDate = date.compareTo(other.date);
            return byDate != 0 ? byDate : source.compareTo(other.source);
        }
    }

    private record SourceKind(PipelineSource source, StepKind kind) implements Comparable<SourceKind> {
        @Override
        public int compareTo(SourceKind other) {
            int bySource = source.compareTo(other.source);
            return bySource != 0 ? bySource : kind.compareTo(other.kind);
        }
    }

    private record SourceCompetition(PipelineSource source, String competition)
            implements Comparable<SourceCompetition> {
        @Override
        public int compareTo(SourceCompetition other) {
            int bySource = source.compareTo(other.source);
            return bySource != 0 ? bySource : competition.compareTo(other.competition);
        }
    }
}
