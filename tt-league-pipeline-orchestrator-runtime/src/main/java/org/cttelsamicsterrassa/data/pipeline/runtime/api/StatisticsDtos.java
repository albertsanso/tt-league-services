package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.PendingByAge;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.ReportingProgress;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunOutcomeStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.SourceHealthStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.TimeToReportStats;

/**
 * Response shapes of the statistics API. Durations are whole seconds; days are local dates in the statistics
 * {@code zone}. The DTOs only carry what {@code StatisticsQueries} computed.
 */
public final class StatisticsDtos {

    private StatisticsDtos() {
    }

    public record DailyDto(String zone, List<DailyRowDto> rows) {

        static DailyDto of(String zone, List<DailyStats> stats) {
            return new DailyDto(zone, stats.stream().map(DailyRowDto::from).toList());
        }
    }

    public record DailyRowDto(
            LocalDate date,
            String source,
            int runs,
            int failures,
            int matchesReported,
            Long avgTimeToReportSeconds,
            int pendingEndOfDay,
            Instant computedAt) {

        static DailyRowDto from(DailyStats stats) {
            return new DailyRowDto(stats.date(), stats.source().name(), stats.runs(), stats.failures(),
                    stats.matchesReported(), seconds(stats.avgTimeToReport()), stats.pendingEndOfDay(),
                    stats.computedAt());
        }
    }

    public record RunOutcomesDto(String zone, List<DayOutcomesDto> days, List<StepAverageDto> stepAverages) {

        static RunOutcomesDto of(String zone, RunOutcomeStats stats) {
            return new RunOutcomesDto(
                    zone,
                    stats.days().stream()
                            .map(d -> new DayOutcomesDto(d.date(), d.source().name(), d.succeeded(), d.noChanges(),
                                    d.partial(), d.failed()))
                            .toList(),
                    stats.stepAverages().stream()
                            .map(a -> new StepAverageDto(a.source().name(), a.kind().name(), a.attempts(),
                                    seconds(a.average())))
                            .toList());
        }
    }

    public record DayOutcomesDto(
            LocalDate date, String source, int succeeded, int noChanges, int partial, int failed) {
    }

    public record StepAverageDto(String source, String kind, int attempts, Long avgStepSeconds) {
    }

    public record TimeToReportDto(String season, List<TimeToReportRowDto> rows) {

        static TimeToReportDto of(String season, TimeToReportStats stats) {
            return new TimeToReportDto(season, stats.rows().stream()
                    .map(r -> new TimeToReportRowDto(r.source().name(), r.competition(), r.count(),
                            seconds(r.median()), seconds(r.p90())))
                    .toList());
        }
    }

    /** {@code competition} is null on the total row of a source. */
    public record TimeToReportRowDto(
            String source, String competition, int count, Long medianSeconds, Long p90Seconds) {
    }

    public record PendingDto(Instant asOf, List<SourcePendingDto> sources) {

        static PendingDto of(PendingByAge pending) {
            return new PendingDto(pending.asOf(), pending.sources().stream()
                    .map(s -> new SourcePendingDto(s.source().name(), s.under1Day(), s.days1To2(), s.days2To7(),
                            s.over7Days(), s.overdue()))
                    .toList());
        }
    }

    public record SourcePendingDto(
            String source, int under1Day, int days1To2, int days2To7, int over7Days, int overdue) {
    }

    public record CorrectionsDto(String zone, List<DayCorrectionsDto> days, List<SourceCorrectionsDto> totals) {

        static CorrectionsDto of(String zone, CorrectionStats stats) {
            return new CorrectionsDto(
                    zone,
                    stats.days().stream()
                            .map(d -> new DayCorrectionsDto(d.date(), d.source().name(), d.amendedPlayed()))
                            .toList(),
                    stats.totals().stream()
                            .map(t -> new SourceCorrectionsDto(t.source().name(), t.amendedPlayed()))
                            .toList());
        }
    }

    public record DayCorrectionsDto(LocalDate date, String source, long amendedPlayed) {
    }

    public record SourceCorrectionsDto(String source, long amendedPlayed) {
    }

    public record SourceHealthDto(String zone, List<DayHealthDto> days, List<SourceHealthTotalDto> totals) {

        static SourceHealthDto of(String zone, SourceHealthStats stats) {
            return new SourceHealthDto(
                    zone,
                    stats.days().stream()
                            .map(d -> new DayHealthDto(d.date(), d.source().name(), d.httpErrors(), d.timeouts(),
                                    d.parseErrors(), d.ingestAttempts(), d.sourceUnavailable(), d.healthUnknown()))
                            .toList(),
                    stats.totals().stream()
                            .map(t -> new SourceHealthTotalDto(t.source().name(), t.httpErrors(), t.timeouts(),
                                    t.parseErrors(), t.ingestAttempts(), t.sourceUnavailable(), t.healthUnknown()))
                            .toList());
        }
    }

    public record DayHealthDto(
            LocalDate date,
            String source,
            long httpErrors,
            long timeouts,
            long parseErrors,
            int ingestAttempts,
            int sourceUnavailable,
            int healthUnknown) {
    }

    public record SourceHealthTotalDto(
            String source,
            long httpErrors,
            long timeouts,
            long parseErrors,
            int ingestAttempts,
            int sourceUnavailable,
            int healthUnknown) {
    }

    public record ReportingProgressDto(
            String source, String season, String zone, List<MatchDayProgressDto> matchDays) {

        static ReportingProgressDto of(String source, String season, String zone, List<ReportingProgress> rows) {
            return new ReportingProgressDto(source, season, zone, rows.stream()
                    .map(r -> new MatchDayProgressDto(r.matchDayId(), r.key().competition(), r.key().groupNumber(),
                            r.key().phase(), r.key().round(), r.state().name(), r.windowStart(), r.windowEnd(),
                            r.active(), r.reported(), r.postponed(), r.pending(),
                            r.points().stream()
                                    .map(p -> new ProgressPointDto(p.date(), p.reported(), p.pending()))
                                    .toList()))
                    .toList());
        }
    }

    public record MatchDayProgressDto(
            UUID matchDayId,
            String competition,
            Integer groupNumber,
            String phase,
            int round,
            String state,
            LocalDate windowStart,
            LocalDate windowEnd,
            int active,
            int reported,
            int postponed,
            int pending,
            List<ProgressPointDto> points) {
    }

    public record ProgressPointDto(LocalDate date, int reported, int pending) {
    }

    private static Long seconds(Duration duration) {
        return duration == null ? null : duration.toSeconds();
    }
}
