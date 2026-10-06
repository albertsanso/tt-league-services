package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DateRange;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsQueries;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only statistics. Any authenticated user can read them. The controller only validates the parameters and calls
 * {@link StatisticsQueries}; it never derives a figure.
 */
@RestController
@RequestMapping("/api/pipeline/statistics")
@Tag(name = "Statistics")
@SecurityRequirement(name = "bearer")
class StatisticsController {

    private final StatisticsQueries queries;

    StatisticsController(StatisticsQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/daily")
    @Operation(summary = "Daily snapshots per source",
            description = "Runs, failures, matches reported, average time to report and pending at end of day, "
                    + "grouped by local day in the statistics zone.")
    @ApiResponse(responseCode = "400", description = "Invalid or missing parameter")
    StatisticsDtos.DailyDto daily(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "source", required = false) List<String> sources) {
        return StatisticsDtos.DailyDto.of(zone(), queries.daily(range(from, to), sources(sources)));
    }

    @GetMapping("/runs")
    @Operation(summary = "Terminal runs by outcome per day and source, and average step durations",
            description = "unitKey keeps the runs that have a finished unit with that key and the steps of that unit.")
    StatisticsDtos.RunOutcomesDto runs(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "source", required = false) List<String> sources,
            @RequestParam(name = "unitKey", required = false) String unitKey) {
        return StatisticsDtos.RunOutcomesDto.of(zone(),
                queries.runs(range(from, to), sources(sources), unitKey(unitKey)));
    }

    @GetMapping("/units")
    @Operation(summary = "Terminal units by outcome per source and unit key, with the average duration",
            description = "Failure rates per unit; unitKey limits the answer to one unit.")
    StatisticsDtos.UnitOutcomesDto units(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "source", required = false) List<String> sources,
            @RequestParam(name = "unitKey", required = false) String unitKey) {
        return StatisticsDtos.UnitOutcomesDto.of(zone(),
                queries.units(range(from, to), sources(sources), unitKey(unitKey)));
    }

    @GetMapping("/time-to-report")
    @Operation(summary = "Median and p90 time to report per source and competition, plus a source total")
    StatisticsDtos.TimeToReportDto timeToReport(
            @RequestParam("season") String season,
            @RequestParam(name = "source", required = false) List<String> sources) {
        requireSeason(season);
        return StatisticsDtos.TimeToReportDto.of(season, queries.timeToReport(season, sources(sources)));
    }

    @GetMapping("/pending")
    @Operation(summary = "Matches still waiting for a result, by age",
            description = "Without a season every season is counted.")
    StatisticsDtos.PendingDto pending(
            @RequestParam(name = "season", required = false) String season,
            @RequestParam(name = "source", required = false) List<String> sources) {
        if (season != null) {
            requireSeason(season);
        }
        return StatisticsDtos.PendingDto.of(queries.pending(sources(sources), season));
    }

    @GetMapping("/corrections")
    @Operation(summary = "Amended actas re-applied per day and source, plus totals")
    StatisticsDtos.CorrectionsDto corrections(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "source", required = false) List<String> sources) {
        return StatisticsDtos.CorrectionsDto.of(zone(), queries.corrections(range(from, to), sources(sources)));
    }

    @GetMapping("/reporting-progress")
    @Operation(summary = "How each match day fills with results over its window")
    @ApiResponse(responseCode = "400", description = "Not exactly one source, or invalid or missing parameter")
    StatisticsDtos.ReportingProgressDto reportingProgress(
            @RequestParam(name = "source", required = false) List<String> sources,
            @RequestParam("season") String season,
            @RequestParam(name = "competition", required = false) String competition) {
        Set<PipelineSource> parsed = sources(sources);
        if (parsed.size() != 1) {
            throw new InvalidRequestException("source", "exactly one source is required");
        }
        requireSeason(season);
        PipelineSource source = parsed.iterator().next();
        String filter = competition == null || competition.isBlank() ? null : competition;
        return StatisticsDtos.ReportingProgressDto.of(source.name(), season, zone(),
                queries.reportingProgress(source, season, filter));
    }

    @GetMapping("/source-health")
    @Operation(summary = "HTTP errors, timeouts and parse errors reported by ingest attempts",
            description = "healthUnknown counts attempts that carry no health data.")
    StatisticsDtos.SourceHealthDto sourceHealth(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "source", required = false) List<String> sources) {
        return StatisticsDtos.SourceHealthDto.of(zone(), queries.sourceHealth(range(from, to), sources(sources)));
    }

    private static Optional<String> unitKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isBlank() || value.length() > 64) {
            throw new InvalidRequestException("unitKey", "unitKey must be 1 to 64 characters");
        }
        return Optional.of(value);
    }

    private String zone() {
        return queries.zone().getId();
    }

    private static DateRange range(LocalDate from, LocalDate to) {
        try {
            return new DateRange(from, to);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("from", e.getMessage(), e);
        }
    }

    private static void requireSeason(String season) {
        try {
            PipelineRun.requireValidSeason(season);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("season", e.getMessage(), e);
        }
    }

    private static Set<PipelineSource> sources(List<String> values) {
        Set<PipelineSource> parsed = EnumSet.noneOf(PipelineSource.class);
        if (values == null) {
            return parsed;
        }
        for (String value : values) {
            try {
                parsed.add(PipelineSource.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("source", "Invalid value for source: " + value
                        + " (expected one of " + Arrays.stream(PipelineSource.values()).map(Enum::name)
                                .collect(Collectors.joining(", ")) + ")", e);
            }
        }
        return parsed;
    }
}
