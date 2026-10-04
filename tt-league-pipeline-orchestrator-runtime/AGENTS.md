# Pipeline orchestrator runtime instructions

These instructions supplement the repository-level `AGENTS.md`.

## Scope

Executable Spring Boot 3.5 (Java 21) orchestrator service. It adapts the ports
of `tt-league-pipeline-orchestrator-core`: HTTP clients, controllers, scheduler
and (in later features) Flyway/JPA persistence and security.

## Boundaries

- Depends only on `tt-league-pipeline-orchestrator-core` among project modules.
  Never depend on `tt-data-league-*` modules.
- The platform (`tt-data-league-api-runtime`) and `tt-league-ingest-rest` are
  reached over HTTP only. Never read or write platform tables.
- Configuration lives in `PipelineOrchestratorProperties`
  (`tt.pipeline.*`, validated). Required values have no defaults in
  `application.yml`, so a missing variable fails startup. Do not add defaults.
- Do not add Testcontainers, Flyway or Spring Security until the feature that
  first uses them.

## Validation

```text
mvn -pl tt-league-pipeline-orchestrator-runtime -am test
```
