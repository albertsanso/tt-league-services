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
and open-match-day scope ports; later features add tracker rules and the
remaining ports.

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
