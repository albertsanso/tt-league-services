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
`RunLauncher` and `RunRecovery`; later features add tracker rules and the
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

## Validation

```text
mvn -pl tt-league-pipeline-orchestrator-core -am test
```
