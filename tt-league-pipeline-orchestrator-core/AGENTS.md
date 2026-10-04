# Pipeline orchestrator core instructions

These instructions supplement the repository-level `AGENTS.md`.

## Scope

Framework-free core of the pipeline orchestrator: run state machine, match-day
tracker rules, polling policy, scope builder and the ports (`IngestGateway`,
`ImportGateway`, `PlatformMatchGateway`, `ArtifactStore`, repositories,
`Notifier`) that `tt-league-pipeline-orchestrator-runtime` implements. The
module is a skeleton until FEAT-00103 onward add behaviour.

## Boundaries

- No Spring, JPA, Hibernate, `java.net.http` or any `tt-data-league-*`
  dependency or import. `CoreDependencyRulesTest` fails the build on violations.
- Only test-scoped dependencies (JUnit Jupiter, AssertJ) are allowed in the POM.
- Root package: `org.cttelsamicsterrassa.data.pipeline.core`.
- Match state is read through platform REST gateways, never through platform
  domain or persistence types.

## Validation

```text
mvn -pl tt-league-pipeline-orchestrator-core -am test
```
