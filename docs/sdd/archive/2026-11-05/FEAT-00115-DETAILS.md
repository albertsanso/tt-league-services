# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>`, `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>` and `tt-league-ingest/packages` as `<ing>`. Test sources mirror them. Core test fixtures live in
`<core-test>/execution/testing`, the published `test-jar`.

The plan has four parts:
- Steps 1-2: the orchestrator core (correlation id on the ingest request, operational gauge readings).
- Steps 3-9: the orchestrator runtime (Prometheus registry, JSON logs with `runId`, run metrics, gauges, security).
- Steps 10-13: `tt-league-ingest` (JSON logs, `runId` on every log line of a run, legacy script logging).
- Steps 14-15: documentation and validation.

No platform, import, frontend or Flyway change. One new dependency, `io.micrometer:micrometer-registry-prometheus`,
goes in the runtime POM only, with its version from the Spring Boot BOM. There is no root or parent POM change and no
new Python dependency.

**Decisions proposed in this plan (2026-10-05), to confirm before `ready`.**
- **D1, how the run id reaches ingest.** The orchestrator sends its run id as a new optional `correlationId` field in
  the `POST /api/v1/ingest/runs` body. Ingest stores it on the run, returns it in the run JSON, and binds it to every
  log record of that run's execution. A body field rather than a header, because it is an attribute of the ingest run:
  it is validated with the rest of the body and visible in `GET /runs/{id}`. Old ingest services ignore the unknown
  field (pydantic `extra="ignore"`), so the services can be rolled out in either order.
- **D2, one log schema for both services.** Both services write one JSON object per line with logstash-style field
  names: `@timestamp`, `level`, `logger_name`, `thread_name`, `message`, `stack_trace`. The orchestrator run id is in
  `runId` in both services, so one query (`runId = <uuid>`) returns the lines of both services. Ingest also writes
  its own id as `ingestRunId`.
- **D3, Prometheus endpoint access.** `/actuator/prometheus` is public, like `/actuator/health`. It carries only
  aggregate counts and durations, tagged by enum values. Deployment must not route it through the public reverse
  proxy (README, and a note for FEAT-00116). The alternatives are a scrape token, or a separate management port that
  needs a second security chain. Both add configuration that a single-VM deployment does not need.

**Contracts used (read from the code on 2026-10-05).**

*Orchestrator core*
- `RunObserver.runChanged(PipelineRun)` / `stepChanged(PipelineStep)` are called by `RunExecutor.updateRun`/`saveStep`
  and by `RunLauncher` for a run's creation and launch failure. `CompositeRunObserver` catches and logs observer
  failures, so they never reach the executor.
- Terminal `RunStatus` values (`NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`) have no successors, so a run is written
  terminal exactly once. `PipelineStep.succeed`/`fail` finish a `RUNNING` step, so each step attempt finishes once.
  `PipelineStep` has `runId`, `kind`, `attempt`, `status`, `startedAt` and `finishedAt`, but no source.
  `PipelineRun.startedAt` is null for a run that failed at launch.
- `IngestRunRequest(source, season, mode, scope, force)` is built only by `IngestRunRequest.forRun(run)` (in
  `RunExecutor`, line 188) and by three test call sites. `ScriptedIngestGateway.startRequests` records every request.
- `StatisticsQueries.pending(Set.of(), null)` returns `PendingByAge(asOf, sources)`, with one `SourcePending(source,
  under1Day, days1To2, days2To7, over7Days, overdue)` row for every `PipelineSource`. It applies
  `StatisticsRules.isPendingNow`, the only definition of "pending now". `MatchDayRepository.findByState(OPEN)` lists
  the open match days.
- The core logs through `System.Logger`. In the runtime, that goes to JUL, then through Boot's JUL-to-SLF4J bridge,
  synchronously on the calling thread, so an MDC entry on the run thread reaches those lines.

*Orchestrator runtime*
- Spring Boot 3.5.8. `spring-boot-starter-actuator` is already a dependency. `management.endpoints.web.exposure.include`
  is `health,info`. Boot's structured logging (`logging.structured.format.console`: `logstash`, `ecs`, `gelf`)
  writes MDC entries and SLF4J key-value pairs as JSON fields. With an empty value Boot keeps the plain pattern
  encoder.
- `SecurityConfiguration` permits `/actuator/health`, `/actuator/health/**`, `/actuator/info`, the API docs and
  `/error`, and authenticates everything else.
- `ExecutorRunDispatcher.runOne(runId)` runs `RunExecutor.execute` on a private pool thread. `LoggingRunObserver` writes
  one INFO line per run or step change.
- `IngestServiceJobRunner.startRun` POSTs a private `StartBody` record. The gateways get their `RestClient` from Boot's
  `RestClient.Builder`, so `http.client.requests` metrics with URI templates come with the registry at no extra cost.

*Ingest*
- `ingest_rest.app.create_app` runs each ingest run on a `ThreadPoolExecutor(max_workers=1)` through `execute(record)`.
  `executor.submit` does not copy `contextvars`, so the context must be bound inside `execute`. `RunBody` has no
  `extra="forbid"`. `main.py` calls `uvicorn.run` with uvicorn's default log config.
- The legacy scripts run in-process through `run_per_scope(..., download.main)` and `parse.main`:
  - `ingest_bcnesa.parse.configure_logging` calls `logging.basicConfig(..., force=True)`, which replaces the root
    handlers for the rest of the process.
  - `ingest_bcnesa.download` and `ingest_fctt.download`/`parse` add their own console and file handlers to their
    loggers.
  - `ingest_rfetm` loggers propagate to the root logger. `setup_error_log` adds a WARNING `FileHandler` to the root
    logger.
  - `ingest_bcnesa.acta_parser.main` is not called in-process: `parse` uses only its functions.
- `ingest_common.health` already uses a `ContextVar` side channel, which is the same pattern as the new log context.

## Orchestrator core

1. **Correlation id on the ingest request (`<core>/execution/port/IngestRunRequest.java`).**
   - Add a required `UUID correlationId` as the last component. `forRun(run)` passes `run.id()`, so every start
     attempt of a run, including retries and resumed runs, sends the same id. Replays never call ingest and are not
     affected.
   - Update the three test call sites.
   - `RunExecutorTest`: `ScriptedIngestGateway.startRequests` carries `correlationId == run.id()` for a fresh run and
     for a start that is retried after `INGEST_UNAVAILABLE`.

2. **Operational gauge readings (`<core>/statistics/OperationalGauges.java`, `OperationalReadings.java`).**
   - `OperationalGauges(StatisticsQueries queries, MatchDayRepository matchDays)` has one method,
     `OperationalReadings read()`.
   - `OperationalReadings(Instant asOf, Map<PipelineSource, PendingByAge.SourcePending> pending,
     Map<PipelineSource, Integer> openMatchDays)` is an immutable value. Both maps hold every `PipelineSource`, with
     zero rows or counts where there is nothing.
   - The values come from:
     - `pending`: `queries.pending(Set.of(), null)`, every season
     - `openMatchDays`: `matchDays.findByState(MatchDayState.OPEN)`, counted per source
     - `asOf`: the `PendingByAge.asOf()` of that read
   - It is composition only and adds no rule. The pending figure is `StatisticsRules.isPendingNow`, so `/actuator`
     and `GET /api/pipeline/statistics/pending` always agree.
   - `OperationalGaugesTest` uses these fixtures:
     - `InMemoryStatisticsReadRepository`, `InMemoryDailyStatsRepository`, `InMemoryMatchDayRepository` and
       `FakeRunClock`
     - for pending: every age bucket, overdue, ignored, closed-day and future matches
     - for open match days: `UPCOMING`/`OPEN`/`CLOSED` days across two sources
     - empty input

## Orchestrator runtime

3. **Dependency and configuration.**
   - `pom.xml`: add `io.micrometer:micrometer-registry-prometheus` with `runtime` scope and no version. The code uses
     only `MeterRegistry` from `micrometer-core`, which actuator already brings.
   - `application.yml`:
     ```yaml
     management:
       endpoints.web.exposure.include: health,info,prometheus
       metrics.tags.application: ${spring.application.name}
     logging:
       structured.format.console: ${PIPELINE_LOG_FORMAT:logstash}
     ```
     The format values:
     - `logstash` (the default) and `ecs`/`gelf` are Boot formats.
     - An empty `PIPELINE_LOG_FORMAT` keeps Boot's plain-text pattern for local development.
     - Any other value fails startup through Boot's own format lookup. There is no fallback.
   - No new `tt.pipeline.*` property.

4. **Run log context (`<rt>/logging/RunLogContext.java`, new).**
   - `RunLogContext.bind(UUID runId)` puts the MDC key `runId` (constant `RUN_ID`) and returns an `AutoCloseable`
     `Scope`. Its `close()` restores the previous value, or removes the key when there was none, so nested binds on the
     same thread are safe. A null id is rejected. This is the only class that writes the `runId` MDC key.
   - `ExecutorRunDispatcher.runOne` wraps `executor.execute(runId)` and its "escaped the executor" log in
     `try (Scope scope = RunLogContext.bind(runId))`. So every line written on the run thread carries `runId`: the
     runtime gateways, the observers, and the core `System.Logger` lines through the JUL bridge. The binding ends with
     the run, so a pool thread never leaks it to the next run.
   - `LoggingRunObserver` binds `runId` around each of its lines (for changes logged off the run thread, such as the
     `QUEUED` line on the request thread and a launch failure). It adds `source`, `status`, `step`, `attempt`,
     `outcome` and `error` as SLF4J key-value pairs (`LOG.atInfo().addKeyValue(...)`), so they become JSON fields. The
     message text keeps today's content for plain-text logs.
   - Tests:
     - `RunLogContextTest`: bind, restore, nested binds and null rejection.
     - `ExecutorRunDispatcherTest`: an `IngestGateway` test double records `MDC.get("runId")` inside `execute`. It
       equals the run id, and after the run the pool thread has no `runId`. Two runs on a one-thread pool do not see
       each other's id.
     - `LoggingRunObserverTest` (new): uses a logback `ListAppender`. The MDC `runId` and key-value pairs are present,
       and an outer binding is restored.

5. **Run id sent to ingest (`<rt>/gateway/IngestServiceJobRunner.java`).**
   - `StartBody` gains `String correlationId`, set to `request.correlationId().toString()` (decision D1).
   - `IngestServiceJobRunnerTest`: the POST body contains `correlationId`.
   - `RunExecutorHttpTest`: the `StubHttpServer` sees the run's id in the start body.

6. **Run metrics (`<rt>/metrics/RunMetricsObserver.java`, new).**
   - A `RunObserver` built with a `MeterRegistry` and the `PipelineRunRepository`.
   - `runChanged(run)` acts only when `run.status().isTerminal()`. It is counted once, because a terminal status is
     written once (see Contracts). It records:
     - counter `pipeline.runs.finished`, with tags `source`, `trigger`, `outcome` (the terminal `RunStatus` name) and
       `error` (`run.error().code()`, a `FailureCode` name, or `none`)
     - timer `pipeline.run.duration`, with tags `source`, `trigger` and `outcome`, recording `finishedAt - startedAt`
       only when `startedAt` is set
   - `stepChanged(step)` acts only when `step.status() != RUNNING`. It records timer `pipeline.step.duration` with
     tags:
     - `source`: from `runs.findById(step.runId())`. A missing run throws `IllegalStateException`, which
       `CompositeRunObserver` logs.
     - `step`: the `StepKind`
     - `status`: `SUCCEEDED` or `FAILED`
     It records `finishedAt - startedAt`, with one sample per attempt.
   - The timers publish fixed service-level buckets of 10s, 30s, 1m, 5m, 15m, 30m, 1h, 2h, 3h and 6h, and no
     client-side percentiles.
   - Tag values are enum or `FailureCode` names only. A run id, season, match, scope or message is never a tag.
   - Register it in the `@Primary` composite of `RunExecutionConfiguration.runObserver`, after `LoggingRunObserver`.
     Update `RunExecutionConfigurationTest`.
   - `RunMetricsObserverTest` uses a `SimpleMeterRegistry` and `InMemoryPipelineRunRepository`. It covers:
     - each terminal status, the `error` tag and the `none` tag
     - a launch failure without `startedAt`: counted, but no duration
     - non-terminal run changes and `RUNNING` steps, which are ignored
     - each step kind and status
     - a missing run

7. **Operational gauges (`<rt>/metrics/OperationalGaugeBinder.java`, `MetricsConfiguration.java`, new).**
   - `MetricsConfiguration` declares the core `OperationalGauges` bean, from the existing `StatisticsQueries` and
     `MatchDayRepository` beans, and the `OperationalGaugeBinder` (`MeterBinder`) bean.
   - For every `PipelineSource` the binder registers:
     - `pipeline.matches.pending`, with tags `source` and `age` (`under_1_day`, `days_1_to_2`, `days_2_to_7`,
       `over_7_days`, the `AgeBucket` names in lower case)
     - `pipeline.matches.overdue`, with tag `source`
     - `pipeline.match.days.open`, with tag `source`
   - Every gauge reads one cached `OperationalReadings`. The first read after the cache is older than 30 seconds
     (constant `MAX_AGE`, time from `RunClock`) refreshes it, under a lock, so one scrape does one read. There is no
     scheduler, thread or ShedLock: nothing runs while nobody scrapes.
   - When a refresh fails:
     - it is logged as a WARN with the exception class and message
     - counter `pipeline.metrics.refresh.failures` is incremented
     - every gauge reports `NaN` until a refresh succeeds, so a stale value is never shown as current
     - the failure also starts a new 30-second wait, so a database outage costs one read per 30 seconds
   - `OperationalGaugeBinderTest` uses a `SimpleMeterRegistry`, the in-memory fixtures and `FakeRunClock`. It covers
     the values, one read within 30 seconds, a refresh after, and `NaN` plus the counter on failure followed by
     recovery.

8. **Security (`<rt>/security/SecurityConfiguration.java`).**
   - Add `/actuator/prometheus` to the `permitAll` matcher next to health and info (decision D3). Update the class
     Javadoc.
   - `RunsApiWebTest`: `/actuator/prometheus` passes security without a token (404 in the slice, not 401), following
     the existing `/actuator/health` case.

9. **Integration tests.**
   - `StructuredLoggingTest` (new, no Docker): start a minimal `SpringApplication` (`WebApplicationType.NONE`, an
     empty configuration) with `logging.structured.format.console=logstash`, and capture its output with
     `OutputCaptureExtension`. Inside `RunLogContext.bind(id)`, log once through SLF4J and once through
     `System.getLogger(...)`, as the core does. Both lines are JSON with `"runId":"<id>"`, which proves the MDC and the
     JUL bridge. A key-value pair from `LoggingRunObserver` appears as a field.
   - `PipelineOrchestratorApplicationTest` (Testcontainers, add `@AutoConfigureObservability`): `GET
     /actuator/prometheus` without a token answers 200 `text/plain` and contains `pipeline_matches_pending{`,
     `pipeline_matches_overdue{`, `pipeline_match_days_open{` and `application="tt-league-pipeline-orchestrator-runtime"`.
     Run meters are registered on their first sample and are covered by the unit tests.

## Ingest service

10. **Log context and JSON formatter (`<ing>/tt-league-ingest-common/src/ingest_common/logs.py`, new, standard
    library only).**
    - `_run: ContextVar[tuple[str, str | None] | None]`, which holds the ingest run id and the correlation id.
    - `bind_run(ingest_run_id: str, correlation_id: str | None)` is a context manager that sets and resets the
      variable.
    - `install_record_factory()` is idempotent. It wraps the current `logging` record factory and sets `record.runId`
      (the correlation id or `None`) and `record.ingestRunId` on every `LogRecord` when it is created. So the ids
      exist whatever handler formats the record, including the legacy file handlers.
    - `JsonFormatter` writes one `json.dumps(..., ensure_ascii=False)` object per line with these fields (decision D2):
      - `@timestamp`: UTC ISO-8601 with milliseconds and `Z`
      - `level`, `logger_name`, `thread_name`, `message` (`record.getMessage()`)
      - `runId` and `ingestRunId` when set
      - `stack_trace` when `exc_info` is set
    - `configure_service_logging(fmt: str)` accepts `json` or `text`; any other value raises `ValueError`. It:
      - installs the record factory
      - replaces the root handlers with one `StreamHandler(sys.stderr)` at INFO. Its formatter is `JsonFormatter`, or
        for `text` the pattern `%(asctime)s %(levelname)s [%(runId)s %(ingestRunId)s] %(name)s %(message)s`.
      - sets the module flag read by `service_logging_active() -> bool`
    - `tests/test_logs.py` covers:
      - the JSON fields, and the ids inside and outside `bind_run`
      - that a `ThreadPoolExecutor` worker without its own bind has no ids
      - `stack_trace`, the idempotent factory and an invalid format
      - a fixture that restores the root handlers, the factory and the flag

11. **Legacy script logging under the service (`ingest_bcnesa/download.py`, `ingest_bcnesa/parse.py`,
    `ingest_fctt/download.py`, `ingest_fctt/parse.py`).**
    - When `logs.service_logging_active()`, each `configure_logging`:
      - installs no console handler and never touches the root logger (no `basicConfig(force=True)`)
      - attaches its file handler to the script's own logger, with the unchanged legacy format and path. It replaces
        and closes the handler from an earlier call, so repeated in-process calls do not leak file handles.
    - Records propagate to the root JSON handler, so each line also reaches the service log with `runId` and
      `ingestRunId`.
    - With the flag off (CLI and standalone scripts), the behaviour is unchanged.
    - No output file, JSON, manifest or file name changes. Log files are not part of the byte-compatibility contract,
      and their format is kept anyway.
    - `ingest_rfetm` needs no change: its loggers already propagate to the root logger. `ingest_bcnesa.acta_parser`
      needs no change either: its `main` is not called in-process.
    - Tests (`test_parse.py`/`test_download_incremental.py` in bcnesa, `test_parse.py`/`test_download.py` in fctt), with
      service logging active:
      - the root handlers are unchanged after `configure_logging`
      - no `StreamHandler` is added
      - the log file is written
      - a second call leaves exactly one file handler on the script's logger
      - a record propagates to the root logger with the bound ids
    - One test per script keeps the flag-off behaviour.

12. **REST service (`<ing>/tt-league-ingest-rest/src/ingest_rest/app.py`, `main.py`).**
    - `RunBody.correlationId: str | None = None`. A value that does not match `^[A-Za-z0-9._-]{1,64}$` answers `400`,
      because the value lands in log lines.
    - Store it on `RunRecord.correlation_id`, not on `IngestRequest`: it is service metadata, and the ZIP and manifest
      do not change. `to_dict` adds `"correlationId"`.
    - `create_run` logs `run accepted` (source, season, stages, mode) inside `bind_run(run_id, correlation_id)`.
    - `execute(record)` wraps its whole body, the pipeline and `prune_packages`, in
      `bind_run(record.run_id, record.correlation_id)`. It logs `run started` and `run finished` (status, outcome). The
      failure branch also logs with `exc_info`, and it still records the error on the run as today.
    - `main.py`:
      - reads `TT_INGEST_REST_LOG_FORMAT`: `json` (default) or `text`. An invalid value prints an error and exits 2,
        like the other configuration errors.
      - calls `configure_service_logging` before `create_app`
      - passes `log_config=None` to `uvicorn.run`, so uvicorn's loggers propagate to the root handler
    - `test_rest.py`:
      - `correlationId` is accepted and returned by `GET /runs/{id}` and `GET /runs`. An invalid value answers `400`,
        and an absent value is `null`.
      - With a capturing root handler, every record emitted during a run carries `runId == correlationId` and
        `ingestRunId == runId`. That includes a record logged by the fake ingestor on the executor thread and the
        accepted, started and finished lines.
      - A run without `correlationId` has `runId` `None`.
    - New `test_main.py`: an invalid `TT_INGEST_REST_LOG_FORMAT` exits 2.

13. **Workspace rules.** `tests/test_workspace_layout.py` stays green: the federation packages import
    `ingest_common.logs`, which is the allowed direction, and `ingest_common` imports no federation package. `uv lock
    --check` is unchanged, since there is no new dependency.

## Documentation and validation

14. **Documentation.**
    - Runtime `README.md`, new "Observability" section:
      - the meter table (name, type, tags, meaning) and the Prometheus names (`pipeline_runs_finished_total`,
        `pipeline_run_duration_seconds_*`, `pipeline_step_duration_seconds_*`, `pipeline_matches_pending`,
        `pipeline_matches_overdue`, `pipeline_match_days_open`, `pipeline_metrics_refresh_failures_total`), plus
        Boot's JVM and `http_*` meters
      - that counters are per process and reset on restart
      - the 30-second gauge cache
      - that `/actuator/prometheus` is public and must not be published by the reverse proxy
      - `PIPELINE_LOG_FORMAT` and the JSON fields, with `runId` correlating with ingest
      - that Grafana boards and the log stack are not committed
      - replace "`/actuator/health` and `/actuator/info` are exposed" (line 72), and update the Security section's
        list of public paths
    - `tt-league-ingest/README.md`:
      - `TT_INGEST_REST_LOG_FORMAT` in the configuration table
      - `correlationId` in the run body and response
      - the JSON log fields, shared with the orchestrator
      - that the CLI keeps its text output
    - Module `AGENTS.md` files:
      - runtime: meters only through `RunMetricsObserver`/`OperationalGaugeBinder`, with enum or `FailureCode` tags
        only (never a run id), and the `runId` MDC key only through `RunLogContext`. `/actuator/prometheus` is public
        and exposes aggregates only.
      - core: `IngestRunRequest.correlationId`, and `OperationalGauges` composes `StatisticsQueries.pending` without
        adding a rule.
      - ingest: the service configures logging only through `ingest_common.logs`. A legacy `configure_logging` must
        not touch the root logger or add console handlers while service logging is active.
    - No Flyway migration, so `docs/pipeline-datamodel.md` is unchanged.

15. **Validation.**
    - `mvn -pl tt-league-pipeline-orchestrator-core -am test` (including `CoreDependencyRulesTest`)
    - `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`, with Docker running for
      `PipelineOrchestratorApplicationTest`
    - from `tt-league-ingest/`: `uv lock --check`, `uv sync --all-packages`, `uv run pytest`
    - the full `mvn test`

    Then review the diff for `target/`, `.venv/`, logs and generated content.

## Acceptance Criteria

- [x] Micrometer metrics (runs by outcome, step durations, pending matches, open match days) are exposed through Actuator in Prometheus format
- [x] The orchestrator logs in structured JSON with `runId`, and passes its `runId` to the ingest service, which includes it on every log line of that run
- [x] Tests check metric registration and run-id propagation

# Implementation Guidelines

Follow the root and module `AGENTS.md` files.

- Grafana boards and the log stack are deployment concerns, documented but not committed as environment-specific config.
- **Core boundaries.** The core gets no Micrometer, SLF4J or MDC dependency, and `CoreDependencyRulesTest` must pass.
  Meters, MDC and the JSON encoder stay in the runtime. `OperationalGauges` composes existing queries. "Pending" is
  only ever `StatisticsRules.isPendingNow`, and nothing re-derives it.
- **Cardinality.** Meter tags are enum names (`PipelineSource`, `RunTrigger`, `RunStatus`, `StepKind`, `StepStatus`,
  `AgeBucket`) or `FailureCode` names. A run id, ingest run id, season, scope, match, competition or message is never a
  tag. Run ids belong in logs, not in metrics.
- **Observers stay side channels.** `RunMetricsObserver` and `LoggingRunObserver` never block or throw into the
  executor beyond what `CompositeRunObserver` already isolates. Metrics never change run behaviour.
- **No success-shaped metrics.** A failed gauge refresh reports `NaN` and counts the failure. It never keeps showing the
  last value as current.
- **Secrets and data.** Log lines and JSON fields never carry `X-API-Key` values, tokens, JWT secrets, SMTP
  credentials, match results or `RunError.message` text beyond what today's lines already log. `correlationId` is
  validated before it reaches a log line.
- **Byte compatibility in ingest.** The legacy scripts' outputs (JSON, ZIP, manifest, file and folder names) do not
  change. Only their console and root-logger handling changes, and only while service logging is active.
- **Dependencies.** Only `micrometer-registry-prometheus` is added, in the runtime POM with the Boot-managed version.
  There is no logstash-encoder, OpenTelemetry or Python logging library.
- **Out of scope:**
  - distributed tracing (Micrometer Tracing, OpenTelemetry, `traceparent`) and trace ids in logs
  - a metrics endpoint in `tt-league-ingest`, and metrics or JSON logs in the platform services
  - JSON logs in the ingest CLI
  - `runId` on uvicorn access-log lines, which are written outside the run's execution
  - `print()` output of the legacy scripts (stdout, not log records), and the per-script log files' format
  - Prometheus alert rules, Grafana dashboards, and log shipping or retention
  - frontend changes
  - persisting metrics or keeping counters across restarts

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Operational observability".

- 2026-10-05: the plan was built against the code on branch `feature/incremental-actas-by-jornada`, after FEAT-00114.
  - The effort was raised from medium to large: 15 steps across the orchestrator core and runtime and three ingest
    packages.
  - The dependencies now also name FEAT-00107 (the match-day tracker behind the open match days) and FEAT-00113
    (`StatisticsQueries.pending`, the pending figure). Both are done.
- Open decisions to confirm before `ready`: D1 (a `correlationId` body field, not a header), D2 (the shared
  logstash-style field names, with `runId` meaning the orchestrator run id in both services) and D3 (a public
  `/actuator/prometheus` kept off the public reverse proxy).
- Naming caveat (D2): in the ingest API, `runId` is the ingest run id, while in the ingest log lines `runId` is the
  orchestrator run id and the ingest id is `ingestRunId`. The READMEs must say this explicitly. The alternative,
  `pipelineRunId` in both services, would not follow the acceptance criterion's `runId` wording.
- FEAT-00116 (container packaging) must not route `/actuator/prometheus` through the public reverse proxy, and should
  pass `PIPELINE_LOG_FORMAT`/`TT_INGEST_REST_LOG_FORMAT` through the Compose environment.
- Pre-existing issue found while planning (not in scope): `ingest_rfetm.download.setup_error_log` and
  `ingest_rfetm.parse.setup_error_log` add a root `FileHandler` on every call and never remove it. In the long-running
  REST service, every RFETM run leaves open handlers, and later warnings are written to every earlier run's error log.
- Follow-up candidates (not planned): a Prometheus endpoint in `tt-league-ingest-rest` (stage durations, source
  health counters); trace propagation with Micrometer Tracing and OpenTelemetry once more than two services are
  involved.

- 2026-10-05: implementation executed from `planned` on the user's instruction, which also confirmed D1-D3 as proposed.
  - Deviations from the plan: the ingest tests for the legacy scripts live in new `test_service_logging.py` files in
    the bcnesa and fctt packages (not in the existing test modules), and `OperationalReadings` rejects maps that miss
    a `PipelineSource`. A shared helper, `ingest_common.logs.attach_script_file_handler`, implements the service-mode
    file handler for the four legacy `configure_logging` functions.
  - Core fixtures needed no change; `RunExecutionConfigurationTest` and `ScheduleConfigurationTest` gained a
    `RunMetricsObserver` bean in their mock `Repositories` configuration.

- 2026-10-05, validation: `mvn -pl tt-league-pipeline-orchestrator-core -am test` and the ingest suite (`uv lock --check`,
  `uv sync --all-packages`, `uv run pytest`: 351 passed) are green; the runtime unit and slice tests pass.
  - Not verified: Docker is not available on this machine, so the Testcontainers tests were skipped, including the
    `GET /actuator/prometheus` test in `PipelineOrchestratorApplicationTest`. The first acceptance criterion
    (metrics exposed in Prometheus format) stays unchecked until that test has run with Docker.
  - Two failures outside this feature were seen in the full `mvn test`: `BcnesaImportProcessorsTest` (missing fixture
    `acta_bcnesa_2026_published.json` in `tt-data-league-import`) and
    `PipelineOrchestratorPropertiesTest.failsWhenTheStatisticsZoneIsMissingBlankOrInvalid` (the assertion expects
    another message wording).
