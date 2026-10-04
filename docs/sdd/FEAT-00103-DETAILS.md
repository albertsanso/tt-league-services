# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-core`: run/step/artifact/import-report values, the transition table from the proposal and repository ports.
2. `tt-league-pipeline-orchestrator-runtime`: Flyway (dependency in the module POM only), JPA entities and adapters, `pipeline` schema.
3. Unit tests for the state machine; Testcontainers tests for persistence and the active-run constraint.
4. `docs/pipeline-datamodel.md` in the runtime module.

## Acceptance Criteria

- [ ] `tt-league-pipeline-orchestrator-core` models `PipelineRun` and `PipelineStep` with states `QUEUED`, `RUNNING_INGEST`, `NO_CHANGES`, `PACKED`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, triggers `SCHEDULED`/`MANUAL`/`RETRY` and `requestedBy`, and rejects illegal transitions
- [ ] Flyway migrations in `tt-league-pipeline-orchestrator-runtime` create schema `pipeline` with `pipeline_run`, `pipeline_step`, `run_artifact` and `import_report`
- [ ] A partial unique index guarantees at most one `QUEUED`/`RUNNING_*`/`PACKED`/`IMPORTING` run per source
- [ ] JPA adapters implement the core repository ports; Testcontainers PostgreSQL tests cover the migrations, the uniqueness rule and state persistence
- [ ] The module documents its tables in a datamodel document next to the module README

# Implementation Guidelines

- No foreign keys into platform tables (D2). Store platform ids (import job id, match UUIDs) as plain columns.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Run lifecycle" and "Tracking data model" (`pipeline_run`, `pipeline_step`, `run_artifact`, `import_report`).
