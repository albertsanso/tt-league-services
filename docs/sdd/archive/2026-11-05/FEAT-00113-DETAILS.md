# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>`, `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>`, `tt-league-pipeline-orchestrator-frontend/src` as `<fe>` and `tt-league-ingest/packages` as `<ingest>`. Test
sources mirror them. Core test fixtures live in `<core-test>/execution/testing`, the published `test-jar`.

The plan has six parts:
- Steps 1-3: `tt-league-ingest`.
- Steps 4-6: the platform modules.
- Steps 7-12: the orchestrator core.
- Steps 13-20: the orchestrator runtime.
- Steps 21-25: the orchestrator frontend.
- Steps 26-27: documentation and validation.

New dependencies:
- Only `@mui/x-charts` is added, in the orchestrator frontend.
- No root or parent POM change.
- No Python dependency change.

**Decisions (2026-10-05, user).**
- **Source health is split by the ingest service.** Each ingest stage report counts `http_errors`, `timeouts` and
  `parse_errors`. The orchestrator stores the totals per INGEST step attempt.
- **Corrections come from a platform counter.** `amendedPlayed` counts the `PLAYED_AMENDED` outcomes of FEAT-00089. It
  is added to the import lifecycle counters and to the import job JSON, and the orchestrator stores it in
  `import_report`.
- **Charts use `@mui/x-charts`**, the MIT community edition.

**Contracts used (read from the code on 2026-10-05).**

*Ingest service*
- `StageReport.counters` is a fixed dict built from `COUNTERS` in `ingest_common/run.py`. It is serialised under
  `stages[].counters` in `GET /api/v1/ingest/runs/{id}` by `RunRecord.to_dict`.
- Issues are free text `(where, message)` pairs. `has_issues` looks only at `issues`, `invalid` and `failed`.
- The legacy downloaders and parsers run through `ingest_common.scan.run_per_scope` / `record_exit_code`. They return
  only an exit code, so the counts need a side channel.
- HTTP failures are handled in these places:
  - `ingest_rfetm/download.py` (~l.185-210, l.447)
  - `ingest_rfetm/teams_download.py` (~l.77-82)
  - `ingest_bcnesa/download.py` (~l.549-607)
  - `ingest_fctt/download.py` (`HttpClient`, ~l.620-690, which already detects `timed_out`)
- Parser failures are handled in these places:
  - `ingest_rfetm/parse.py` (l.459, l.498)
  - `ingest_bcnesa/parse.py` (l.223, l.276, l.306)
  - `ingest_fctt/parse.py` (l.558, l.679)

*Orchestrator core and runtime*
- `IngestServiceJobRunner.getRun` maps only `status`, `outcome`, `retryable`, `package` and `error` into
  `IngestRunState`.
- `RunExecutor.finishIngest` finishes the INGEST `PipelineStep` with `step.succeed(now, outcome)` or `failStep(...)`.
- `MatchTracking` has `matchDateTime`, `firstSeenAt` and `reportedAt`. `MatchDayTracker` sets
  `reportedAt = run.finishedAt()` (or `now`), and `MatchTracking.first` sets `firstSeenAt = now`. A match first seen
  already `REPORTED` therefore has `reportedAt <= firstSeenAt`, and its arrival time is unknown.
- `MatchDayKey.competition` is the platform competition name. The tracker has no separate category, so the
  **competition is the category** in every statistic.

*Platform*
- `ImportLifecycleCounters` has six components. `ImportRunContext.lifecycleCounters()` builds them from
  `matchOutcomeCounts`.
- `PLAYED_AMENDED` is counted there but not exported.
- Amended-acta detection is off by default (`IMPORT_EXECUTION_AMENDED_ACTA_DETECTION=disabled`).

*Background work*
- The pattern is a private scheduler that is never an `Executor` bean, with a ShedLock lock taken through
  `LockingTaskExecutor` (`TrackerRecomputeSchedule`, `ScheduledRunTrigger`).

**Definitions (decided here).** All day boundaries are local days in the configured statistics zone (step 15).

| Figure | Definition |
|---|---|
| Arrival | A tracked match with `reportedAt > firstSeenAt`, so its result arrived while it was tracked. Matches first seen already reported are left out of every time-to-report figure and of `matchesReported`. |
| Time to report | `reportedAt - matchDateTime` for arrivals with `matchDateTime` set and `reportedAt >= matchDateTime`. |
| `daily_stats.runs` / `failures` | Terminal runs of the source with `finishedAt` in the day, and those of them with status `FAILED`. |
| `daily_stats.matches_reported` | Arrivals of the source with `reportedAt` in the day. |
| `daily_stats.avg_time_to_report_seconds` | Mean time to report of those arrivals. Null when there are none. |
| `daily_stats.pending_end_of_day` | Tracked matches of the source that are not ignored, whose day is not `CLOSED`, with `matchDateTime < end of day`, not reported by the end of the day (`reportedAt` null or `>= end of day`), and whose current status is not `POSTPONED`. It is computed from the state at aggregation time, so a day aggregated late is an approximation. |
| Median / p90 | Nearest-rank percentile over the sorted durations: the element at index `ceil(p * n) - 1`. |
| Pending (now) | Matches in `UPCOMING`/`OPEN` days that are not ignored, with status `SCHEDULED`, `AWAITING_RESULT` or `OVERDUE` and `matchDateTime <= now`. Undated matches are left out. |
| Pending age buckets | `UNDER_1_DAY`, `DAYS_1_TO_2`, `DAYS_2_TO_7` and `OVER_7_DAYS`, measured from `matchDateTime`, with each lower bound inclusive. `overdue` is the number of pending matches with status `OVERDUE`. |
| Corrections | The sum of `import_report.amended_played` per source, by the day of `received_at`. |
| Source health | Per source and day of the INGEST attempt's `finished_at`: the sums of `http_errors`, `timeouts` and `parse_errors`, plus three counts: attempts, attempts with outcome `SOURCE_UNAVAILABLE`, and attempts without health data. |

1. **Ingest health side channel** (`<ingest>/tt-league-ingest-common/src/ingest_common/health.py`).
   - Add a `HealthCounters` dataclass with `http_errors`, `timeouts` and `parse_errors`.
   - Add a `collect()` context manager that installs a collector in a `contextvars.ContextVar` and yields it.
   - Add three module functions: `http_error()`, `timeout()` and `parse_error()`. Each one increments the active
     collector, and does nothing when no collector is installed, which is the case when a legacy script runs
     standalone.
   - Add `"http_errors"`, `"timeouts"` and `"parse_errors"` to `COUNTERS` in `run.py`.
   - Leave `has_issues`, `status` and `outcome` unchanged. The legacy exit codes already flag these failures.
   - In `scan.run_per_scope`, wrap each `main(argv)` call in `health.collect()` and add the collector's values to the
     stage report counters. Do the same around the two `record_exit_code` calls of the rfetm TEAMS stage
     (`ingest_rfetm/ingestor.py`).
2. **Instrument the legacy downloaders and parsers.** Make one call per URL or document that finally fails, after the
   legacy retries. Do not count retried attempts.
   - Downloads:
     - A `requests.Timeout`, `socket.timeout` or `TimeoutError` (including a `URLError` whose reason is a timeout)
       calls `health.timeout()`.
     - Any other final failure calls `health.http_error()`. That covers a status error (including "acta not
       available" HTTP statuses), a connection error and a retryable status that has run out of attempts.
     - Change rfetm `download.py` and `teams_download.py`, bcnesa `download.py` and fctt `download.py`, at the places
       listed above.
   - Parsers: an exception while reading or converting one source document calls `health.parse_error()`. Change rfetm
     `parse.py` l.459 and l.498, bcnesa `parse.py` l.223, l.276 and l.306, and fctt `parse.py` l.558 and l.679.
   - Write errors (`OSError` while saving) are not parse errors.
   - No change to delays, retries, User-Agent, output files or log text. Byte-compatible output is a hard rule.
3. **Ingest tests and docs.**
   - Add `tests/test_health.py`: the collector, the no-op without a collector, and nested scopes summed per
     `run_per_scope` call.
   - Add one test per federation that drives a stubbed session or `urlopen` raising a timeout and an HTTP 404. It
     asserts the stage counters `timeouts == 1` and `http_errors == 1`, and that the outcome is unchanged.
   - Add one parser test per federation with a broken page, asserting `parse_errors == 1`.
   - Update every test that asserts the exact counter keys or the REST stage JSON.
   - In the ingest `README.md`, document the three counters in the run JSON section.
4. **Platform counter** (`tt-data-league-core-domain`).
   - Add a seventh component `amendedPlayed` to `ImportLifecycleCounters`: stored PLAYED matches re-applied from an
     amended acta (FEAT-00089).
   - Include it in `ZERO`, `plus`, `hasActivity` and the javadoc.
   - Add `long amendedPlayed` after `unresolvedPendingFixtures` in `ImportProcessResultDto`, and fill it in
     `ImportProcessResultDtoMapper` and `ImportRunStatusDtoMapper`.
   - Update every `new ImportLifecycleCounters(` call site listed by
     `grep -rn "new ImportLifecycleCounters(" --include=*.java`, tests included.
5. **Platform import and persistence.**
   - `ImportRunContext.lifecycleCounters()` adds
     `matchOutcomeCounts.getOrDefault(MatchLifecycleOutcome.PLAYED_AMENDED, 0)`.
   - `PLAYED_AMENDMENT_REPORTED` (report mode) is not counted, because nothing was written.
   - In `tt-data-league-core-repository-jpa`, `ImportProcessResultJsonCodec.LifecycleJson` gets `amendedPlayed`.
     Stored results written before this change have no such key and must decode to `0`. Add a codec test with a
     legacy JSON fixture.
   - Print the new counter in the `tt-data-league-import-runtime` `App` summary.
6. **Platform tests and docs.**
   - Extend `ImportLifecycleCountersTest`.
   - Add an `ImportRunContext` test: a `PLAYED_AMENDED` outcome yields `amendedPlayed == 1`.
   - Extend the `ImportJobHandlersTest`, `FindImportRunStatusQueryHandlerTest` and `ImportJobRepositoryJpaTest`
     assertions.
   - In the `tt-data-league-api-runtime` and `tt-data-league-import-runtime` READMEs, add `amendedPlayed` next to
     `upgradedToPlayed`. Note that it stays `0` unless `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION=write`.
7. **Core run model** (`<core>/run`, `<core>/execution`).
   - Add `IngestHealth(long httpErrors, long timeouts, long parseErrors)` in `<core>/run`. Each value is `>= 0`.
   - `PipelineStep` gets a nullable `ingestHealth`:
     - It is set only on INGEST steps (`IllegalArgumentException` otherwise).
     - It is set only when the step finishes, through new `succeed(now, outcome, health)` and
       `fail(now, outcome, error, retryable, health)` overloads.
     - `restore` takes it.
   - `IngestRunState` gets a nullable `IngestHealth health`.
   - `RunExecutor.finishIngest` passes `state.health()` to the finishing step for every finished outcome
     (`NO_CHANGES`, `SUCCEEDED`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE` and `FAILED`). Steps that fail before
     the ingest run finished (timeouts, lost runs) carry none.
   - `ImportCounters` and `ImportReport` get `amendedPlayed` (non-negative), and `ImportReportMapper` sums it over
     seasons.
   - Update `ScriptedIngestGateway` so a scripted state can carry health, and update the in-memory step and
     import-report repositories.
8. **Statistics model** (`<core>/statistics`).
   - `StatisticsSettings(ZoneId zone, LocalTime dailyAt, int backfillDays)`, where `backfillDays` is 0-366.
   - `DateRange(LocalDate from, LocalDate to)`: inclusive, `from <= to`, at most 366 days. It has
     `startInstant(zone)` and `endInstant(zone)` (exclusive).
   - `DailyStats(LocalDate date, PipelineSource source, int runs, int failures, int matchesReported,
     Duration avgTimeToReport /*nullable*/, int pendingEndOfDay, Instant computedAt)`, with `failures <= runs` and
     non-negative counts.
   - Read facts as plain records:
     - `RunFacts(runId, source, status, startedAt, finishedAt)`
     - `StepFacts(runId, source, kind, status, outcome, startedAt, finishedAt, IngestHealth health)`
     - `MatchFacts(matchId, matchDayId, source, season, competition, dayState, status, matchDateTime, firstSeenAt,
       reportedAt, ignored)`
     - `CorrectionFacts(runId, source, receivedAt, amendedPlayed)`
   - Result values:
     - `RunOutcomeStats`: per day and source, counts by terminal status, plus the average duration per `StepKind` and
       source over the range. The average covers finished attempts, succeeded and failed.
     - `TimeToReportStats`: rows of source, competition (null for the source total), count, median and p90.
     - `PendingByAge`: per source, the bucket counts and `overdue`.
     - `CorrectionStats`: per day and source, plus the totals per source.
     - `SourceHealthStats`: per day and source, plus the totals per source.
     - `ReportingProgress`: per match day, its key, state, `windowStart`/`windowEnd` (`last_date + grace_days`),
       `active` (not ignored), `reported`, `postponed`, `pending` and the points. Each point is `date`, `reported`
       and `pending`, with `reported` as the arrivals or reported matches with `reportedAt < end of date` and
       `pending = active - reported - postponed`. There is one point per date from `firstDate` to
       `min(windowEnd, today)`. Undated match days are left out.
9. **StatisticsRules** (`<core>/statistics/StatisticsRules`). This is a pure, static class: no I/O and no clock. It is
   the only place that applies the definitions table:
   - `isArrival`
   - `timeToReport` (`Optional<Duration>`)
   - `percentile(sortedDurations, p)`
   - `isPendingAt(match, endOfDay)` for `pending_end_of_day`
   - `isPendingNow(match, now)`
   - `ageBucket(matchDateTime, now)`
   - `dailyStats(source, date, runs, matches, zone, computedAt)`
10. **Ports** (`<core>/statistics/port`).
    - `StatisticsReadRepository` is read-only. Every method is source-filtered, and an empty set means all sources:
      - `terminalRunsFinishedBetween(Instant from, Instant to, Set<PipelineSource>)`
      - `stepsFinishedBetween(from, to, sources)`
      - `matchesBySeason(sources, String season)`
      - `matchesForDay(Instant start, Instant end)`, a SQL prefilter: `reportedAt` in `[start, end)`, or
        `matchDateTime < end` and (`reportedAt` null or `>= end`) and not ignored. The rules are applied again in
        the core.
      - `importReportsReceivedBetween(from, to, sources)`
    - `DailyStatsRepository`:
      - `upsert(List<DailyStats>)`, keyed by `(date, source)`
      - `find(DateRange, Set<PipelineSource>)`, ordered by date, then source
      - `Optional<LocalDate> latestDate()`
11. **Services** (`<core>/statistics`).
    - `DailyStatsAggregator(StatisticsReadRepository, DailyStatsRepository, RunClock, StatisticsSettings)` has two
      methods:
      - `aggregate(LocalDate day)` writes one row per `PipelineSource`, zero rows included, in a single `upsert`.
        It rejects days that are not complete (`day >= today` in the zone).
      - `catchUp()` aggregates every day from `max(latestDate + 1, today - backfillDays)` through yesterday, oldest
        first. It returns `AggregationOutcome(List<LocalDate> days)` and never recomputes a stored day.
      Repository failures propagate.
    - `StatisticsQueries` has one read method per endpoint (step 17). Methods taking a `DateRange` read with the
      settings zone. `reportingProgress(source, season, competition)` reads through the existing
      `MatchDayRepository.findBySourceAndSeason` and `findMatches`. Nothing is cached and nothing is written.
12. **Core tests** (`<core-test>/statistics`).
    - `StatisticsRulesTest` covers every row of the definitions table at its boundaries:
      - arrival versus first-seen-reported
      - a negative time to report is excluded
      - the percentile on n = 1, 2 and 10
      - the edges of each age bucket
      - pending at end of day for postponed, ignored, closed and undated matches
      - day boundaries across a DST change in `Europe/Madrid`
    - `DailyStatsAggregatorTest` uses new fixtures `InMemoryDailyStatsRepository` and
      `InMemoryStatisticsReadRepository`, added to the `test-jar`:
      - catch-up from empty uses `backfillDays`
      - catch-up after a gap
      - today is never aggregated
      - every source gets a row
      - stored days are not recomputed
    - `StatisticsQueriesTest`: each result shape and the `DateRange` validation.
    - `PipelineStepTest` and `RunExecutorTest`: health on each finished ingest outcome, rejected on non-INGEST steps.
    - `ImportReportMapperTest`: `amendedPlayed`.
    - `CoreDependencyRulesTest` must stay green.
13. **Flyway `V8__statistics.sql`.**
    - `pipeline_step` gets `http_errors`, `timeouts` and `parse_errors` (`bigint NULL`, each `>= 0`). A CHECK makes
      the three all null or all set, and allows them to be set only when `kind = 'INGEST'`.
    - `import_report` gets `amended_played bigint NOT NULL DEFAULT 0 CHECK (amended_played >= 0)`. Existing rows read
      `0`, which the datamodel documents as "not recorded before V8".
    - New table `daily_stats`:
      - `stat_date date`
      - `source varchar(16)`, with a CHECK on the three sources
      - `runs`, `failures`, `matches_reported` and `pending_end_of_day` (`integer NOT NULL >= 0`)
      - `avg_time_to_report_seconds bigint NULL >= 0`
      - `zone varchar(64) NOT NULL`
      - `computed_at timestamptz NOT NULL`
      - Constraints: `PRIMARY KEY (stat_date, source)` and `CHECK (failures <= runs)`.
    - Indexes: `ix_pipeline_run_finished (finished_at)`, `ix_pipeline_step_finished (finished_at)`,
      `ix_match_tracking_reported (reported_at)` and `ix_import_report_received (received_at)`.
    - No foreign keys from `daily_stats`, because it is a snapshot.
14. **JPA adapter** (`<rt>/persistence`).
    - `PipelineStepEntity` and `JpaPipelineStepRepository` map the health columns. `ImportReportEntity` and
      `JpaImportReportRepository` map `amended_played`.
    - Add `DailyStatsEntity` (`@IdClass` or an embedded id), `DailyStatsJpaRepository` and `JpaDailyStatsRepository`.
      `upsert` uses `saveAll` in one transaction.
    - Add `JpaStatisticsReadRepository implements StatisticsReadRepository`. It uses JPQL constructor projections over
      the existing entities, with `MatchTrackingEntity` joined to `MatchDayEntity` for the source, season,
      competition and state. No native SQL and no aggregation in SQL: percentiles and buckets stay in the core.
15. **Configuration** (`PipelineOrchestratorProperties`).
    - Add a required `Statistics statistics` component:
      - `zone` (required and valid, with no default)
      - `dailyAt` (`LocalTime`)
      - `backfillDays` (0-366)
    - It has `statisticsSettings()`.
    - Add these to `application.yml`:

      | Setting | Variable | Default |
      |---|---|---|
      | `tt.pipeline.statistics.zone` | `PIPELINE_STATISTICS_ZONE` | none |
      | `daily-at` | `PIPELINE_STATISTICS_DAILY_AT` | `00:30` |
      | `backfill-days` | `PIPELINE_STATISTICS_BACKFILL_DAYS` | `31` |

    - Add the zone to every test property set and to the README configuration table.
16. **Gateways.**
    - `IngestServiceJobRunner.RunStateBody` gets `stages: List<StageBody(String stage, Map<String, Long> counters)>`.
      `getRun` builds the `IngestHealth` as follows:
      - `httpErrors` and `timeouts` are the sums over all stages.
      - `parseErrors` is the sum over the `parse` and `teams` stages of `parse_errors + invalid`.
      - Health is `null` when no stage carries the three new keys (an older ingest service). A negative value is a
        `PROTOCOL_ERROR`.
    - `HttpImportGateway` reads `amendedPlayed` per season result, with null mapped to `0` like the existing counters.
    - Extend `IngestServiceJobRunnerTest` and `HttpImportGatewayTest`.
17. **Statistics API** (`<rt>/api/StatisticsController`, base `/api/pipeline/statistics`).
    - Every endpoint is `GET` and any authenticated user can call it (it falls under `anyRequest().authenticated()`).
    - `source` is optional and repeatable, `from`/`to` are ISO dates and required, and `season` is required where
      listed.
    - Invalid or missing parameters return `400` through `InvalidRequestException`.

    | Path | Parameters | Returns |
    |---|---|---|
    | `/daily` | `from`, `to`, `source` | `daily_stats` rows (time to report in seconds), plus `zone` |
    | `/runs` | `from`, `to`, `source` | per day and source: `succeeded`, `noChanges`, `partial`, `failed`; `avgStepSeconds` per source and step kind |
    | `/time-to-report` | `season`, `source` | per source and competition, plus a source total: `count`, `medianSeconds`, `p90Seconds` |
    | `/pending` | `source`, optional `season` | per source: the four buckets and `overdue`, plus `asOf` |
    | `/corrections` | `from`, `to`, `source` | per day and source `amendedPlayed`, plus totals |
    | `/reporting-progress` | `source` (exactly one), `season`, optional `competition` | the match-day rows and points of step 8 |
    | `/source-health` | `from`, `to`, `source` | per day and source: `httpErrors`, `timeouts`, `parseErrors`, `ingestAttempts`, `sourceUnavailable`, `healthUnknown`; plus totals |

    - The DTO records live in `<rt>/api/StatisticsDtos`, and wiring is in a `StatisticsConfiguration` with
      `StatisticsQueries` as a bean.
    - Add the endpoints to the OpenAPI configuration where the other controllers are listed.
18. **Daily job** (`<rt>/statistics/DailyStatsSchedule implements SmartLifecycle`).
    - Use a private `ThreadPoolTaskScheduler`, never a bean, with no `@EnableScheduling`.
    - Schedule a `CronTrigger("0 <mm> <HH> * * *", zone)` built from `dailyAt`, plus one `catchUp()` one minute after
      start.
    - Each tick runs `aggregator.catchUp()` under the ShedLock lock `pipeline-daily-stats` (lock at most `PT10M`)
      through `LockingTaskExecutor`.
    - Log `AggregationOutcome` at INFO when days were written.
    - Catch failures at the task boundary like `TrackerRecomputeSchedule`: log a WARN, and the next tick retries.
19. **Runtime tests.**
    - `StatisticsMigrationTest` (Testcontainers): the health CHECKs (a set on a non-INGEST step fails), the
      `daily_stats` key and CHECKs, and the `amended_played` default.
    - Add round trips to `JpaPipelineStepRepositoryTest` and `JpaImportReportRepositoryTest`.
    - `JpaDailyStatsRepositoryTest`: upsert replaces a row, `find` order, `latestDate`.
    - `JpaStatisticsReadRepositoryTest`: each query's filter edges.
    - `StatisticsControllerTest` (`@WebMvcTest` pattern of the existing controllers): 401 without a token, 400 for a
      bad range, a missing season or several sources on `/reporting-progress`, and the JSON shape of each endpoint.
    - `DailyStatsScheduleTest`: the trigger is built from `dailyAt` and the zone, and a failure does not stop the
      next tick.
    - `PipelineOrchestratorPropertiesTest`: a missing or invalid zone and an out-of-range `backfillDays` fail with
      the setting named.
    - `PipelineOrchestratorApplicationTest`: the context loads.
    - A `StatisticsIntegrationTest` drives a run through the HTTP stubs. The ingest stub returns stage counters with
      the new keys, and the platform stub returns `amendedPlayed: 2`. The test asserts the step health,
      `import_report.amended_played`, `/source-health`, `/corrections`, and a `daily_stats` row after `aggregate`.
20. **Run detail.** `ImportReportDto` gets `amendedPlayed`, and `StepDto` gets a nullable `health`
    (`httpErrors`, `timeouts`, `parseErrors`). Update `RunDtoMapper` and its test.
21. **Frontend dependency and API.**
    - Add `@mui/x-charts` (a major version compatible with `@mui/material` 7 and React 19) and regenerate
      `package-lock.json` (see the frontend `AGENTS.md` `edgesOut` note).
    - Add `<fe>/api/statistics.ts` with one function per endpoint, and the DTO types in `<fe>/api/types.ts`.
    - Extend `ImportReport` and `Step` with the run-detail fields of step 20, and show `amendedPlayed` and the step
      health in `RunDetailPage`.
22. **Statistics state** (`<fe>/statistics`).
    - `statisticsFilters.ts` holds the filter state: sources, season (from the existing match-day facets), and a
      `from`/`to` range that defaults to the last 30 days. It reads and writes URL search params like
      `runFilters.ts` does.
    - `useStatistics(filters)` loads every endpoint in parallel with one `AbortController`. It exposes `loading`,
      `error` and `reload`, and reloads on filter change.
    - `useReportingProgress(source, season, competition)` is loaded separately because it needs exactly one source.
    - No event subscription: the page has a refresh button.
    - `format.ts` turns seconds into hours with one decimal.
23. **Dashboard components** (`<fe>/statistics`). Each panel is a MUI `Card` holding an `@mui/x-charts` chart and a
    compact summary table. The table is the accessible text equivalent, and the tests use it.
    - `DailyOverviewPanel`: runs and failures bars, matches reported, and the average time to report per day from
      `/daily`.
    - `RunOutcomesPanel`: stacked bars per day by outcome, and a table of the average step duration.
    - `TimeToReportPanel`: median and p90 bars per source, with an expandable per-competition table.
    - `PendingByAgePanel`: stacked bars per source by age bucket, with the overdue count.
    - `CorrectionsPanel`: bars per day and source, with the totals.
    - `ReportingProgressPanel`: a match-day picker (competition filter and a table with the percentage reported) and a
      line chart of reported versus pending over the window of the selected match day.
    - `SourceHealthPanel`: one card per source with the HTTP error, timeout and parse error totals, plus
      source-unavailable and health-unknown attempts. It also shows a stacked daily bar chart, with a chip that
      highlights a source with parse errors in the range.
24. **StatisticsPage.** Replace the placeholder. Keep the `Statistics` heading, which `App.test.tsx` relies on. Show the
    filter bar, then the panels in a responsive grid. The empty state reads "No statistics for this range", the error
    state is an `Alert` with a retry, and loading shows skeletons. Durations are shown in hours. The page notes that
    days are grouped in the server `zone`.
25. **Frontend tests.**
    - `statisticsFilters.test.ts`: defaults and URL round trip.
    - `format.test.ts`.
    - `useStatistics.test.tsx`: parallel load, abort on filter change, error.
    - `StatisticsPage.test.tsx` with `fakeFetch` fixtures in `<fe>/test/statisticsFixtures.ts`: every panel's table
      shows the fixture figures, a filter change refetches with the new query, the error and empty states, and
      reporting progress for a selected match day.
    - Extend `RunDetailPage.test.tsx` for `amendedPlayed` and health.
    - If `@mui/x-charts` needs `ResizeObserver` in jsdom, stub it in `<fe>/test/setup.ts`.
26. **Documentation.**
    - `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`:
      - a `daily_stats` section (definitions, writer `DailyStatsAggregator`, snapshot, no foreign keys)
      - the new `pipeline_step` and `import_report` columns
      - the `pipeline-daily-stats` lock name under `shedlock`
      - a `V8` row in the migration history
    - Runtime `README.md`: a `## Statistics` section (definitions table, endpoints, daily job timing and catch-up,
      zone, the approximation of a late-aggregated `pending_end_of_day`, and corrections that depend on platform
      amended-acta detection), plus the new variables.
    - `tt-league-pipeline-orchestrator-core/AGENTS.md`: a "Statistics package" paragraph. `StatisticsRules` is the
      only place for the definitions; the aggregator is the only writer of `daily_stats`; the queries are read-only.
      List the new fixtures.
    - `tt-league-pipeline-orchestrator-runtime/AGENTS.md`: `DailyStatsSchedule` has a private scheduler and the
      ShedLock lock `pipeline-daily-stats`; the statistics controller only calls `StatisticsQueries`.
    - `tt-league-pipeline-orchestrator-frontend/AGENTS.md`: charts use `@mui/x-charts` only, statistics state goes
      through the `src/statistics/` hooks, and figures are never derived in the browser.
    - The ingest `README.md` (step 3) and the platform READMEs (step 6).
27. **Validation.** Run these:
    - `uv lock --check`, `uv sync --all-packages` and `uv run pytest` from `tt-league-ingest/`
    - `mvn -pl tt-data-league-core-domain,tt-data-league-import,tt-data-league-core-repository-jpa -am test`
    - `mvn -pl tt-league-pipeline-orchestrator-core -am test`
    - `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`, with Docker running for the Testcontainers tests
    - `mvn -pl tt-league-pipeline-orchestrator-frontend -am test`
    - the full `mvn test`

    Then review the diff for `target/`, `node_modules/`, `dist/`, `.venv` and generated content.

## Acceptance Criteria

- [x] A daily job aggregates `daily_stats` (runs, failures, matches reported, average time to report, pending at end of day) per source
- [x] Statistics endpoints return reporting progress per match day, time to report (median, p90) per source and category, pending by age, corrections after first report and runs by outcome
- [x] A dashboard page charts these figures and a source-health panel (HTTP errors, timeouts, parse errors per source)
- [x] The ingest run report counts HTTP errors, timeouts and parse errors per stage, and the orchestrator stores them per ingest step
- [x] Platform import results count amended actas re-applied (`amendedPlayed`), and the orchestrator stores them per import report
- [x] Tests cover the aggregation and the endpoints

# Implementation Guidelines

Follow the root and module `AGENTS.md` files.

- The core stays framework-free. `StatisticsRules` is the single place for every definition, and the services, the
  runtime and the UI never re-derive a figure. The frontend only formats server values. `CoreDependencyRulesTest` must
  pass.
- Statistics are read-only side channels. They never block or fail a run, a recompute or a request.
  `DailyStatsAggregator` is the only writer of `daily_stats`. Health and `amendedPlayed` are written only with the
  existing step and import-report writes.
- Platform match state still comes only from the tracker's stored data. Statistics never call the platform, never
  store results, and never reference platform tables.
- Ingest instrumentation must not change output files, delays, retries, User-Agent, log text, `status` or `outcome`.
  Count final failures only.
- The platform counter is additive. Older stored import results decode to `0`, and an older ingest service yields
  `health = null` ("unknown"). That is not a silent default for malformed data: a present but negative or non-numeric
  value is a protocol error.
- Schema changes go only through `V8__statistics.sql`, with the datamodel document updated in the same change.
  `daily_stats` has no foreign keys.
- No `@EnableScheduling`, scheduler bean or `Executor` bean. The daily job uses ShedLock like the tracker recompute.
- `PIPELINE_STATISTICS_ZONE` is required with no default, and the startup fails with the setting named.
- Out of scope:
  - Prometheus, Micrometer, Grafana and run-correlated logging (FEAT-00115)
  - replay import (FEAT-00114)
  - an alerts REST API or UI
  - a per-URL `source_fetch` table, and ETag tracking
  - live (SSE) refresh of the dashboard
  - CSV export
  - backfilling health or corrections for runs before V8
  - enabling amended-acta detection by default

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "History, statistics and observability" and UI view 4. `source_fetch` health data comes from ingest run issues; a per-URL table is not planned.

- 2026-10-05 planning decisions (user):
  - Source health is split by new ingest stage counters (`http_errors`, `timeouts`, `parse_errors`) and stored per
    INGEST step.
  - Corrections come from a new platform lifecycle counter, `amendedPlayed`.
  - Charts use `@mui/x-charts`.
  - Two acceptance criteria were added for the ingest counters and the platform counter. The feature now spans
    `tt-league-ingest`, the platform core-domain/import/JPA modules and the three orchestrator modules.
- 2026-10-05 planning decisions (plan):
  - "Category" is the platform competition name (`MatchDayKey.competition`), because the tracker has no separate
    category.
  - Matches first seen already reported (`reportedAt <= firstSeenAt`) are excluded from the time-to-report figures and
    from `matches_reported`, so the first tracker pass does not skew the figures.
  - Percentiles use the nearest rank.
  - `pending_end_of_day` is computed from the state at aggregation time.
  - The daily job runs at `00:30` in the statistics zone and catches up to 31 days.
- 2026-10-05: Plan approved by the user; status set to `ready`.
- Open:
  - Corrections stay at zero until the platform runs with `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION=write`. Decide
    whether to enable it on the deployment (FEAT-00116).
  - Whether to also count retried (recovered) HTTP failures as a separate health signal.
- 2026-10-05 implementation and validation:
  - All 27 plan steps were implemented. Choices made while building: `@mui/x-charts` 9.14.0 (compatible with
    `@mui/material` 7); one chart category per server row (day and source), so the browser never sums figures; the
    reporting-progress percentage is the only figure the UI computes (`reported / active`, as the plan lists it).
  - Passing: `uv lock --check` and ingest `pytest` (314), core (446), platform core-domain/import/JPA (import has one
    pre-existing failure, see below), runtime unit and web tests, the new `StatisticsMigrationTest`,
    `JpaDailyStatsRepositoryTest`, `JpaStatisticsReadRepositoryTest`, `JpaPipelineStepRepositoryTest` and the end-to-end
    `StatisticsIntegrationTest` against real PostgreSQL (Podman, `TESTCONTAINERS_RYUK_DISABLED=true` and
    `TESTCONTAINERS_HOST_OVERRIDE=<VM ip>`; each integration class run on its own), and the frontend (lint, tsc, 382 tests).
  - Not green, none caused by this feature: `BcnesaImportProcessorsTest` needs `acta_bcnesa_2026_published.json`, which
    is not in git; 14 older runtime persistence tests fail (exception translation, step ordering, Hibernate null
    precedence); `TrackerRecomputeIntegrationTest` needs `HttpSecurity` (it uses `WebEnvironment.NONE`). These were never
    exercised without Docker. `PipelinePersistenceTest` now uses `WebEnvironment.MOCK` so the persistence tests can load.
  - `CalendarPage.test.tsx` timed out once in the full parallel run and passed on rerun.
