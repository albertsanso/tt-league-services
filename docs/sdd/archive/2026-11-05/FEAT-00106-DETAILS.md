# Build Plan
Scheduled ticks are a thin adapter over the existing `TriggerRun` path: one core class builds the scheduled
command, the runtime owns the cron timers and the ShedLock lock. No new run states, triggers or API endpoints.

1. **Core: `ScheduledRunTick`** (`tt-league-pipeline-orchestrator-core`, package `...pipeline.core.trigger`).
   - `public final class ScheduledRunTick` with `public static final String REQUESTED_BY = "system:scheduler"`,
     constructor `(TriggerRun triggerRun, String season)` (season checked with `PipelineRun.requireValidSeason`).
   - `public TriggerRun.Outcome tick(PipelineSource source)` calls `triggerRun.trigger(new Command(List.of(source),
     season, ScopeType.FULL_SEASON, List.of(), false, RunTrigger.SCHEDULED, REQUESTED_BY, ConflictMode.REJECT))`
     and returns its single outcome.
   - Always `ConflictMode.REJECT`, independent of `tt.pipeline.triggers.conflict-mode`: a tick for a source with an
     active run is skipped, never stored as a pending trigger. Log `Created` at INFO (run id) and `Rejected` at INFO
     as "skipped: active run <id>" with `System.Logger`; `Unavailable` cannot occur for `FULL_SEASON`, so treat it
     as an `IllegalStateException`.
   - Update `trigger/package-info.java` and the core `AGENTS.md` trigger-package paragraph to name
     `ScheduledRunTick` as the only scheduled caller of `TriggerRun`.

2. **Core tests: `ScheduledRunTickTest`** using the `test-jar` fixtures (`InMemoryPipelineRunRepository`,
   `InMemoryPendingTriggerRepository`, `RecordingDispatcher`, `FakeRunClock`, `StubOpenMatchDayScopeResolver`,
   `RecordingPendingTriggerEvents`):
   - idle source → one `SCHEDULED` run, `FULL_SEASON` scope, `force=false`, `requestedBy=system:scheduler`,
     dispatched once;
   - source with an active run → `Rejected` (`ACTIVE_RUN`), no new run, no pending trigger, no pending-trigger event;
   - other sources are untouched by a tick; an invalid season fails construction.

3. **Parent POM dependency management** (root `pom.xml`, `dependencyManagement` only, inline versions like the
   existing entries): `net.javacrumbs.shedlock:shedlock-spring` and
   `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`, same latest 6.x release supporting Spring Boot 3.5 /
   Spring 6.2 (check Maven Central when implementing and record the version in `# Notes`). Add both to the
   runtime `pom.xml` without versions. `spring-jdbc` already comes with `spring-boot-starter-data-jpa`.

4. **Configuration: `PipelineOrchestratorProperties.Schedule`** (runtime `config` package).
   - New component `@Valid Schedule schedule` (nullable: a missing `tt.pipeline.schedule` means nothing is
     scheduled, normalised in the outer compact constructor to an empty `Schedule`).
   - `record Schedule(String season, String zone, Duration lockAtMostFor, Duration lockAtLeastFor,
     Map<PipelineSource, SourceSchedule> sources)` and `record SourceSchedule(String cron)`. Season, zone and cron
     bind as `String` so blank placeholders do not hit a converter.
   - Compact constructor: drop entries whose `cron` is null/blank; parse the rest with
     `org.springframework.scheduling.support.CronExpression.parse`, failing with
     `schedule.sources.<SOURCE>.cron is not a valid cron expression: <value>`. When at least one source is
     scheduled: `season` required and valid (`PipelineRun.requireValidSeason`, message names `schedule.season`),
     `zone` required and valid (`ZoneId.of`, message names `schedule.zone`), both lock durations positive and
     `lock-at-least-for <= lock-at-most-for`. Expose `scheduledSources()` (sorted by enum order),
     `cron(PipelineSource)` and `zoneId()`.
   - `application.yml`:
     ```yaml
     tt.pipeline.schedule:
       season: ${PIPELINE_SCHEDULE_SEASON:}
       zone: ${PIPELINE_SCHEDULE_ZONE:}
       lock-at-most-for: ${PIPELINE_SCHEDULE_LOCK_AT_MOST_FOR:PT10M}
       lock-at-least-for: ${PIPELINE_SCHEDULE_LOCK_AT_LEAST_FOR:PT30S}
       sources:
         RFETM: { cron: "${PIPELINE_SCHEDULE_RFETM_CRON:}" }
         BCNESA: { cron: "${PIPELINE_SCHEDULE_BCNESA_CRON:}" }
         FCTT: { cron: "${PIPELINE_SCHEDULE_FCTT_CRON:}" }
     ```
     Empty cron placeholders are the opt-out, not a default schedule; season and zone have no usable default and
     fail startup only when a cron is set.
   - Expose `Schedule` as a bean in `PipelineSettingsConfiguration`, like `Triggers` and `Events`.

5. **Flyway `V3__scheduler_lock.sql`** (runtime `db/migration`): the ShedLock JDBC table in schema `pipeline`:
   ```sql
   CREATE TABLE pipeline.shedlock (
       name       varchar(64)  NOT NULL PRIMARY KEY,
       lock_until timestamp(3) NOT NULL,
       locked_at  timestamp(3) NOT NULL,
       locked_by  varchar(255) NOT NULL
   );
   ```
   Not a JPA entity, so `ddl-auto: validate` is unaffected.

6. **Runtime scheduler: package `...pipeline.runtime.schedule`**.
   - `ScheduleConfiguration` (`@Configuration(proxyBeanMethods = false)`): beans `LockProvider` =
     `JdbcTemplateLockProvider` (`withJdbcTemplate(new JdbcTemplate(dataSource))`, `withTableName("pipeline.shedlock")`,
     `usingDbTime()` so all instances share the database clock), `LockingTaskExecutor` =
     `DefaultLockingTaskExecutor(lockProvider)`, and `ScheduledRunTrigger(Schedule, TriggerRun, LockingTaskExecutor)`,
     which builds its `ScheduledRunTick` only when a source is scheduled (the season is blank otherwise). Do **not** use `@EnableScheduling` or
     `@EnableSchedulerLock`: Boot would then register a `taskScheduler` bean, which is an `Executor`.
   - `ScheduledRunTrigger implements SmartLifecycle`: on `start()` creates a **private** single-thread
     `ThreadPoolTaskScheduler` (thread prefix `pipeline-schedule-`, not a bean, same rule as `ExecutorRunDispatcher`
     and `RunEventBroadcaster`) and schedules one `CronTrigger(cron, zoneId)` per scheduled source; with no scheduled
     source it logs "no source schedule configured" and creates nothing. `stop()` cancels the futures and shuts the
     scheduler down. Log each registered source, cron and zone at INFO.
   - Each task runs `lockingExecutor.executeWithLock((Runnable) () -> tick.tick(source),
     new LockConfiguration(Instant.now(), "pipeline-schedule-" + source, lockAtMostFor, lockAtLeastFor))`. A tick
     whose lock is held elsewhere does nothing (log DEBUG). No broad catch: the scheduler's error handler logs a
     failed tick and the next tick still fires; add a test that proves it.
   - Missed ticks while the service is down are not caught up (document it). A tick that fires before startup
     recovery only sees the stale active run and is skipped, which is harmless.

7. **Runtime tests.**
   - `PipelineOrchestratorPropertiesTest`: no cron anywhere → nothing scheduled and blank season/zone accepted;
     invalid cron fails naming `schedule.sources.RFETM.cron`; cron with blank or malformed season fails naming
     `schedule.season`; cron with blank or unknown zone fails naming `schedule.zone`; non-positive lock durations and
     `lock-at-least-for > lock-at-most-for` fail; whitespace-only cron counts as unscheduled.
   - `ScheduledRunTriggerTest` (no Spring context, fake `LockProvider`, real `TriggerRun` over `test-jar` fixtures):
     only sources with a cron are registered, with their own expression and zone; running a registered task takes
     lock `pipeline-schedule-<SOURCE>` with the configured durations and creates a `SCHEDULED` run; a lock provider
     that returns empty leaves the run repository untouched; an active run makes the tick skip; a tick that throws
     does not cancel the next execution; `stop()` shuts the private scheduler down.
   - `ScheduleConfigurationTest` (`ApplicationContextRunner`, mocked repositories and `DataSource` like
     `RunExecutionConfigurationTest`): the context adds no `Executor`/`TaskScheduler` bean, and starts with no
     source scheduled.
   - `SchedulerLockMigrationTest` (Testcontainers, like `ManualTriggersMigrationTest`): `V3` creates
     `pipeline.shedlock` with its primary key.
   - `SchedulerLockPersistenceTest` (Testcontainers, `@PipelinePersistenceTest`/`AbstractPersistenceTest`): two `JdbcTemplateLockProvider`s on the same datasource (two instances) → while one
     holds `pipeline-schedule-RFETM` the other's `executeWithLock` does not run; within `lock-at-least-for` after
     release it still does not run; after it expires it runs.
   - Run `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` with Docker so the persistence tests execute,
     then the full `mvn test`.

8. **Documentation.**
   - Runtime `README.md`: new "Scheduled runs" section (per-source cron variables, `PIPELINE_SCHEDULE_SEASON`,
     `PIPELINE_SCHEDULE_ZONE`, lock tuning, Spring six-field cron format with an example such as `0 0 7,22 * * *`,
     skip-when-active regardless of conflict mode, `requestedBy=system:scheduler`, no catch-up of missed ticks,
     ShedLock behaviour with several instances); add the variables to the configuration tables (season and zone
     marked "required when any cron is set").
   - `docs/pipeline-datamodel.md`: `shedlock` table section and a `V3` row in the migration history.
   - Runtime `AGENTS.md`: scheduled runs are created only through `ScheduledRunTick`/`TriggerRun`; the scheduler
     pool is private and never a bean; no `@EnableScheduling`.

## Acceptance Criteria

- [x] Each source has an explicitly configured cron expression; a source without one is never scheduled, and an invalid expression fails startup
- [x] Scheduled ticks create `SCHEDULED` runs through the same trigger path as manual runs and skip a source with an active run
- [x] ShedLock (JDBC, `pipeline` schema) ensures only one orchestrator instance fires a tick
- [x] Tests cover tick handling, skip-when-active and lock behaviour; README documents the configuration

# Implementation Guidelines

- Scheduling is opt-in per source; no default schedule (root `AGENTS.md`: no silent defaults). Season and zone
  are required as soon as one cron is set; the host time zone is never used implicitly.
- `ScheduledRunTick` only builds a `TriggerRun.Command`; it never calls `RunLauncher` or the repositories. All
  validation, conflict detection and run creation stay in `TriggerRun` and the active-run partial index.
- Ticks always use `ConflictMode.REJECT` (skip); the configured manual conflict mode does not apply to them.
- Keep the core free of Spring: cron parsing, `CronTrigger`, ShedLock and the scheduler thread live in the
  runtime only (`CoreDependencyRulesTest`).
- Scheduler pool rules match `ExecutorRunDispatcher`: private, not a bean, no `@EnableScheduling` or
  `@EnableSchedulerLock` (both would register an `Executor`/proxy infrastructure the runtime avoids).
- Schema change only through the new `V3__scheduler_lock.sql`; never edit `V1`/`V2`. Update
  `docs/pipeline-datamodel.md` in the same change.
- Parent POM change is limited to the two ShedLock `dependencyManagement` entries.
- Out of scope: scoped/adaptive schedules and the weekly full-scope run (FEAT-00108), alerts on skipped or
  failed ticks (FEAT-00112), tick metrics (FEAT-00115), an API to change schedules at runtime, catch-up of
  missed ticks, and distributing run *execution* across instances (ShedLock only guards the tick).

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal rollout phase 2 exit: no manual uploads needed. Replaced in practice by adaptive polling once that item lands; keep it as the fallback mode.

## Planning notes (2026-10-05)

- Plan built against the code delivered by FEAT-00103..FEAT-00105: `TriggerRun.Command` already accepts
  `RunTrigger.SCHEDULED`, and its Javadoc names it the path for scheduled triggers, so no core contract changes.
- Decision: scheduled runs need a season because `TriggerRules` requires one; it is configured explicitly
  (`PIPELINE_SCHEDULE_SEASON`, shared by all sources) instead of being derived, to avoid a silent default. Deriving
  the current season from the platform calendar can replace it later.
- Decision: `requestedBy` for scheduled runs is `system:scheduler` (the colon cannot clash with platform usernames
  used as JWT subjects).
- Decision: a private `SmartLifecycle` scheduler plus `LockingTaskExecutor` instead of `@Scheduled` +
  `@SchedulerLock`, because cron expressions are per source (dynamic) and Boot's `taskScheduler` bean would be an
  `Executor` bean.
- Even without ShedLock, the active-run partial index makes a duplicate tick fail as `ACTIVE_RUN`; ShedLock prevents
  the duplicate attempt and the noisy log, and `lock-at-least-for` covers clock skew between instances.
- FEAT-00108 can reuse `ScheduledRunTick` for its weekly full-scope run and replace the per-source cron in adaptive
  mode; keep this mode as the fallback.
- ShedLock artifacts are not in the local Maven repository yet; the first build downloads them.
- Added FEAT-00105 to the registry dependencies: `TriggerRun` and the pending-trigger path came with it.

## Implementation notes (2026-10-05)

- ShedLock version: **6.10.0**, the last 6.x release (Spring 6.2.10 baseline). 7.x targets Spring 7 / Boot 4 and
  does not fit Spring Boot 3.5.8.
- Deviation from step 3: the runtime declares `shedlock-core` and `shedlock-provider-jdbc-template`, not
  `shedlock-spring`. Without `@EnableSchedulerLock` nothing from `shedlock-spring` is used; `LockProvider`,
  `DefaultLockingTaskExecutor`, `LockConfiguration` and `ClockProvider` come from `shedlock-core`.
- `ScheduledRunTrigger` (runtime `schedule` package) is a `SmartLifecycle` that owns a private single-thread
  `ThreadPoolTaskScheduler` (daemon, prefix `pipeline-schedule-`, logging error handler). It builds its
  `CronTrigger`s and `ScheduledRunTick` in the constructor, so the bean fails fast on an inconsistent schedule.
- `Schedule.sources()` keeps only scheduled sources (blank crons are dropped, values trimmed); an unknown source key
  fails binding. Lock durations are validated whenever present and required once a source is scheduled.
- Validation: `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` passes (core 197 tests; runtime 159 tests,
  53 skipped). Docker is not installed on this machine, so every Testcontainers test was skipped, including the new
  `SchedulerLockMigrationTest` and `SchedulerLockPersistenceTest`. To compensate, a scratch program ran the `V3`
  DDL and the production `JdbcTemplateLockProvider` settings (`usingDbTime`) against the local PostgreSQL in a
  throwaway schema (dropped afterwards): held-lock exclusion, `lock-at-least-for`, expiry of a dead instance's lock
  after `lock-at-most-for` and independent per-source locks all behaved as expected. Run the persistence tests with
  Docker before closing the feature.
- A ShedLock provider caches which lock rows exist; deleting rows under a live provider makes its next attempt
  fail. Tests that clear `pipeline.shedlock` must create fresh providers afterwards (the persistence test does).

- Full `mvn test -Dmaven.test.failure.ignore=true`: every module builds and every test passes except
  `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas` in `tt-data-league-import`, which
  fails at `HEAD` too: it reads `actas/acta_bcnesa_2026_published.json`, which was never committed. Unrelated to this
  feature and left untouched.
- Acceptance check: per-source cron opt-in and startup failures (`PipelineOrchestratorPropertiesTest`);
  `SCHEDULED` runs through `TriggerRun` with skip-when-active (`ScheduledRunTickTest`, `ScheduledRunTriggerTest`);
  ShedLock JDBC lock in `pipeline.shedlock` (`V3`, `ScheduleConfiguration`, lock tests: the unit ones ran, the
  Testcontainers ones still need Docker, see above); README, data model and both `AGENTS.md` files updated.
