# Pipeline orchestrator core instructions

These instructions supplement the repository-level `AGENTS.md`.

## Scope

Framework-free core of the pipeline orchestrator: run state machine, match-day
tracker rules, polling policy, scope builder and the ports (`IngestGateway`,
`ImportGateway`, `PlatformMatchGateway`, `ArtifactStore`, repositories,
`Notifier`) that `tt-league-pipeline-orchestrator-runtime` implements. The
`run` package holds the run state machine (`RunStatus`, `PipelineRun`), the step,
artifact and import-report values, and the repository ports with their
exceptions. The `execution` package holds the execution ports, `RunExecutor`,
`RunLauncher` and `RunRecovery`. The `trigger` package holds `TriggerRun`, the
single path that creates manual and scheduled runs, with the pending-trigger
and open-match-day scope ports. The `tracker` package holds the match-day tracker
(see below). The `polling` package holds the adaptive polling (see below); later features add the remaining ports.

## Boundaries

- No Spring, JPA, Hibernate, `java.net.http` or any `tt-data-league-*`
  dependency or import. `CoreDependencyRulesTest` fails the build on violations.
- Only test-scoped dependencies (JUnit Jupiter, AssertJ) are allowed in the POM.
- Root package: `org.cttelsamicsterrassa.data.pipeline.core`.
- Match state is read through platform REST gateways, never through platform
  domain or persistence types.

## Execution package

- JDK types only (`InputStream`, `Duration`, `System.Logger`); no logging
  library. Time and waiting go through `RunClock`; never call `Thread.sleep` or
  `Instant.now()` in the core.
- `RunExecutor.execute(runId)` is the only entry point and serves fresh and
  resumed runs. Every run change goes through `runs.update`, every step change
  through `steps.save`, and both notify `RunObserver`.
- Only the retries listed in the executor retry rule exist: a timeout,
  `INGEST_BUSY`, `IMPORT_SHRINK` and `IMPORT_FAILED` are final. Failure codes are
  the `FailureCode` names; messages never carry keys or header values.
- `src/test/java/.../execution/testing` is published as the core `test-jar`
  (`maven-jar-plugin` `test-jar`, a build plugin, so the POM still has only
  test-scoped dependencies). Keep those fixtures in step with the ports.

## Trigger package and observers

- `TriggerRun` is the only caller of `RunLauncher` for new runs. Sources are
  processed independently; a pending trigger stores the request, not the
  resolved scope.
- `ScheduledRunTick` is the only scheduled caller of `TriggerRun`: a
  full-season, non-forced `SCHEDULED` run with `ConflictMode.REJECT`, so a
  source with an active run is skipped and never queued. Cron parsing, timers
  and locks stay in the runtime.
- `CompositeRunObserver` (and the pending-trigger listener calls in
  `TriggerRun`) are the only broad `RuntimeException` catches: observers are
  side channels, so every failure is logged and never reaches the executor.
- New fixtures in the `test-jar`: `InMemoryPendingTriggerRepository`,
  `StubOpenMatchDayScopeResolver` and `RecordingPendingTriggerEvents`.

## Validation

```text
mvn -pl tt-league-pipeline-orchestrator-core -am test
```

## Tracker package

- `TrackerRules` is the only place that maps a platform `calendarState` to a `TrackedMatchStatus` and decides when a
  match day opens, closes or reopens. The tracker never derives postponed, overdue or awaiting-result from dates or
  rounds: the platform is the only source of match states and the grace period comes from its response.
- Every platform read is source- and season-scoped; calendars are read only for the competitions of the selected
  jornadas. No platform DTO is reused: `PlatformMatchGateway` returns plain JDK values.
- `MatchDayTracker.recompute` reads and decides first and writes one `MatchDayChangeSet` at the end, so an
  inconsistent or failed recompute writes nothing. No retries, partial writes or fallback to another calendar read.
- Never store results. Platform match ids are plain references; events keep the match id of a removed match.
- `MatchDayRepository.apply` is the only write path and checks versions (`StaleMatchDayException`); the aggregates
  are immutable and every change returns a new instance. Ignoring is a flag next to the status, not a status.
- `TrackerRunObserver` only requests a recompute (`RecomputeRequests`) and never blocks or throws.
- `TrackerRules.completion` is the only place that decides the completion category (`COMPLETE`, `IN_PROGRESS`,
  `HAS_OVERDUE`, `FUTURE`) of a match day, over its active (non-ignored) match counts; `MatchDaySummary` exposes them
  (`ignoredByStatus`, `activeCounts`, `reportedCount`, `totalCount`, `completion`). `RecomputeOutcome.hasChanges` is
  true only for a visible change, not for a recompute that only refreshed timestamps.

## Alert package

- `AlertRules.holding` is the only place that decides which alert conditions hold (pure function, no I/O, no clock);
  the texts are built in `AlertTexts` and describe runs by `RunError.code`, never by `RunError.message`. The
  evaluator, the dispatcher and the UI never re-derive a condition.
- `AlertEvaluator` is the only writer of alerts: it clears the alerts whose condition stopped holding, raises the new
  ones (`ActiveAlertExistsException` means another writer won: skip, do not send) and sends one `Notification` per
  pass. A `NotificationException` is recorded on the alerts (`notifyFailed`) and retried by the next pass; gateway and
  repository failures propagate to the runtime dispatcher.
- `Notifier` and `AlertRequests` implementations never throw into their callers; `AlertRunObserver` only requests an
  evaluation and `NotifyingPollingAlerts` swallows sink failures after calling its delegate.
- New fixtures in the `test-jar`: `InMemoryAlertRepository`, `RecordingNotifier` and `RecordingAlertRequests`;
  `InMemoryMatchDayRepository` also implements `findByState` and `findClosedSince`.

## Polling package

- `PollingPolicy` is a pure function (no I/O, no clock): the level of a unit comes from the tracked status and date of
  its candidate matches in the schedule zone, never re-derived from the platform. Back-off, caps and the
  `OVERDUE_LIMIT` stop live only there; `PollSchedule` is the immutable aggregate and `decided` returns `this` when
  nothing changes.
- `AdaptivePollingTick` is the only adaptive caller of `TriggerRun`: `SCHEDULED`, `ConflictMode.REJECT`, requested by
  `system:polling`, at most one run per tick (the `FULL_REFRESH` run, else one `GROUP` run over the union of the due
  units). Every write goes through `PollScheduleRepository.save`; a `StalePollScheduleException` abandons the tick.
  Cron, timers and locks stay in the runtime.
- `SourceVocabulary` and `BcnesaCompetitionNames` mirror the import path-to-identity rules without depending on
  `tt-data-league-import`; each rule is pinned by `SourceVocabularyTest`. Update them in the same change as the import
  rules. An open match day without an ingest status row fails with `SCOPE_UNMATCHED`: never widen the scope silently.
- `MatchDayRefresh` is the operator refresh of one match day: it builds the filters with the same `ScopeBuilder` as
  `OPEN_MATCH_DAYS` and creates the run only through `TriggerRun` (`GROUP`, `MANUAL`); a created or queued run is
  recorded with `MatchDayActions.recordRefresh` (a `REFRESH_REQUESTED` event), a rejected or unavailable one records
  nothing.
- `TrackerOpenMatchDayScopeResolver` answers `NO_OPEN_MATCH_DAYS`, `NO_INGEST_STATUS` and `SCOPE_UNMATCHED`.
  `PollingAlerts` implementations must not throw into the tick.
- New fixtures in the `test-jar`: `InMemoryPollScheduleRepository`, `InMemoryPollPolicyRepository`,
  `ScriptedIngestStatusGateway` and `RecordingPollingAlerts`.
