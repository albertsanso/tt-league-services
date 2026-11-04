package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/**
 * The only place that applies the statistics definitions. Pure and static: no I/O and no clock, so every figure the
 * aggregator, the queries and the API show comes from the same rules.
 *
 * <ul>
 *   <li><b>Arrival</b>: a tracked match with {@code reportedAt > firstSeenAt}, so its result arrived while it was
 *       tracked. A match first seen already reported has an unknown arrival time and is left out of every
 *       time-to-report figure.
 *   <li><b>Time to report</b>: {@code reportedAt - matchDateTime} for arrivals with a match date and
 *       {@code reportedAt >= matchDateTime}.
 *   <li><b>Pending at end of day</b>: not ignored, day not CLOSED, dated before the end of the day, not reported by
 *       then and not POSTPONED.
 *   <li><b>Pending now</b>: not ignored, day not CLOSED, status SCHEDULED, AWAITING_RESULT or OVERDUE and dated at or
 *       before now.
 * </ul>
 */
public final class StatisticsRules {

    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final Duration TWO_DAYS = Duration.ofDays(2);
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    private StatisticsRules() {}

    public static boolean isArrival(MatchFacts match) {
        return match.reportedAt() != null
                && match.firstSeenAt() != null
                && match.reportedAt().isAfter(match.firstSeenAt());
    }

    public static Optional<Duration> timeToReport(MatchFacts match) {
        if (!isArrival(match) || match.matchDateTime() == null || match.reportedAt().isBefore(match.matchDateTime())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(match.matchDateTime(), match.reportedAt()));
    }

    /** Nearest-rank percentile: the element at index {@code ceil(p * n) - 1} of the sorted durations. */
    public static Duration percentile(List<Duration> sortedDurations, double p) {
        if (sortedDurations.isEmpty()) {
            throw new IllegalArgumentException("durations must not be empty");
        }
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException("p must be in (0, 1]");
        }
        int index = (int) Math.ceil(p * sortedDurations.size()) - 1;
        return sortedDurations.get(Math.max(index, 0));
    }

    public static boolean isPendingAt(MatchFacts match, Instant endOfDay) {
        return !match.ignored()
                && match.dayState() != MatchDayState.CLOSED
                && match.status() != TrackedMatchStatus.POSTPONED
                && match.matchDateTime() != null
                && match.matchDateTime().isBefore(endOfDay)
                && (match.reportedAt() == null || !match.reportedAt().isBefore(endOfDay));
    }

    public static boolean isPendingNow(MatchFacts match, Instant now) {
        return !match.ignored()
                && match.dayState() != MatchDayState.CLOSED
                && match.matchDateTime() != null
                && !match.matchDateTime().isAfter(now)
                && (match.status() == TrackedMatchStatus.SCHEDULED
                        || match.status() == TrackedMatchStatus.AWAITING_RESULT
                        || match.status() == TrackedMatchStatus.OVERDUE);
    }

    /** Age of a pending match, from its match date, with each lower bound inclusive. */
    public static AgeBucket ageBucket(Instant matchDateTime, Instant now) {
        Duration age = Duration.between(matchDateTime, now);
        if (age.compareTo(SEVEN_DAYS) >= 0) {
            return AgeBucket.OVER_7_DAYS;
        }
        if (age.compareTo(TWO_DAYS) >= 0) {
            return AgeBucket.DAYS_2_TO_7;
        }
        if (age.compareTo(ONE_DAY) >= 0) {
            return AgeBucket.DAYS_1_TO_2;
        }
        return AgeBucket.UNDER_1_DAY;
    }

    public static boolean isOnDay(Instant instant, LocalDate date, ZoneId zone) {
        return instant != null
                && !instant.isBefore(startOf(date, zone))
                && instant.isBefore(startOf(date.plusDays(1), zone));
    }

    public static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    /**
     * The stored figures of one source and day. {@code runs} and {@code matches} may hold more than the day needs;
     * each is filtered again here, so a repository prefilter can stay loose.
     */
    public static DailyStats dailyStats(
            PipelineSource source,
            LocalDate date,
            List<RunFacts> runs,
            List<MatchFacts> matches,
            ZoneId zone,
            Instant computedAt) {
        Instant start = startOf(date, zone);
        Instant end = startOf(date.plusDays(1), zone);
        List<RunFacts> dayRuns = runs.stream()
                .filter(run -> run.source() == source
                        && run.status().isTerminal()
                        && run.finishedAt() != null
                        && !run.finishedAt().isBefore(start)
                        && run.finishedAt().isBefore(end))
                .toList();
        int failures = (int) dayRuns.stream().filter(run -> run.status() == RunStatus.FAILED).count();
        List<MatchFacts> sourceMatches =
                matches.stream().filter(match -> match.source() == source).toList();
        List<MatchFacts> arrivals = sourceMatches.stream()
                .filter(match -> isArrival(match)
                        && !match.reportedAt().isBefore(start)
                        && match.reportedAt().isBefore(end))
                .toList();
        List<Duration> times = arrivals.stream()
                .map(StatisticsRules::timeToReport)
                .flatMap(Optional::stream)
                .toList();
        int pending = (int) sourceMatches.stream().filter(match -> isPendingAt(match, end)).count();
        return new DailyStats(
                date, source, dayRuns.size(), failures, arrivals.size(), mean(times), pending, computedAt);
    }

    /**
     * Terminal units counted by outcome per source and unit key, with the mean duration of the units that ran. The
     * label is the one of the newest unit of the key. Sorted by source, then unit key.
     */
    public static List<UnitOutcomeStats.UnitOutcomes> unitOutcomes(List<UnitFacts> units) {
        Map<String, List<UnitFacts>> grouped = new TreeMap<>();
        for (UnitFacts unit : units) {
            grouped.computeIfAbsent(unit.source().ordinal() + "/" + unit.unitKey(), key -> new ArrayList<>())
                    .add(unit);
        }
        List<UnitOutcomeStats.UnitOutcomes> outcomes = new ArrayList<>();
        for (List<UnitFacts> group : grouped.values()) {
            UnitFacts newest = group.stream().max(Comparator.comparing(UnitFacts::finishedAt)).orElseThrow();
            List<Duration> durations = group.stream()
                    .filter(unit -> unit.startedAt() != null)
                    .map(unit -> Duration.between(unit.startedAt(), unit.finishedAt()))
                    .toList();
            outcomes.add(new UnitOutcomeStats.UnitOutcomes(newest.source(), newest.unitKey(), newest.label(),
                    count(group, UnitStatus.SUCCEEDED), count(group, UnitStatus.NO_CHANGES),
                    count(group, UnitStatus.PARTIAL), count(group, UnitStatus.FAILED),
                    count(group, UnitStatus.SKIPPED), mean(durations)));
        }
        return outcomes;
    }

    private static int count(List<UnitFacts> units, UnitStatus status) {
        return (int) units.stream().filter(unit -> unit.status() == status).count();
    }

    /** Mean rounded to the second; null when there are no durations. */
    public static Duration mean(List<Duration> durations) {
        if (durations.isEmpty()) {
            return null;
        }
        long totalMillis = durations.stream().mapToLong(Duration::toMillis).sum();
        return Duration.ofSeconds(Math.round(totalMillis / 1000.0 / durations.size()));
    }
}
