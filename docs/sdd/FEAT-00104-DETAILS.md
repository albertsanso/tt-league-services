# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-core`: `IngestGateway`, `ImportGateway`, `ArtifactStore` ports; `RunExecutor` service and retry policy.
2. `tt-league-pipeline-orchestrator-runtime`: HTTP adapters (Spring `RestClient`) for ingest (`X-API-Key`) and platform (service credential);
   filesystem `ArtifactStore` under an explicitly configured directory; bounded executor.
3. Tests with stubbed servers; README configuration section.

## Acceptance Criteria

- [ ] `IngestServiceJobRunner` starts a `tt-league-ingest-rest` run (`download`, `parse`, `package`) for the run's source and scope, polls it and maps its `outcome` to `NO_CHANGES`, `PACKED` or `FAILED`
- [ ] On `PACKED`, the ZIP is fetched, its SHA-256 verified and stored in a configured artifact directory (`run_artifact` row), then submitted to the platform import jobs API
- [ ] The import job is polled to completion; its counters are stored in `import_report` and the run ends `SUCCEEDED`, `PARTIAL` or `FAILED`
- [ ] Every step has a configurable timeout; `SOURCE_UNAVAILABLE` and HTTP 5xx are retried up to 3 times with exponential back-off, other failures are not
- [ ] An ingest run that disappears (ingest restarted, 404) fails the step with a clear, retryable error
- [ ] Tests use stubbed HTTP servers (no network) for success, no-change, retry, timeout and import-failure paths

# Implementation Guidelines

- The ingest service runs one ingestion at a time across sources; account for its queue in step timeouts.
- Docker/Kubernetes job runners are out of scope (D4). Keep `JobRunner` an interface so they can be added.
- 2026-10-04: with the single-VM Docker Compose deployment (D9) the ingest-REST runner is the only planned runner;
  orchestrator and ingest reach each other by Compose service name, configured explicitly.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Job runner", "Importer client" and "Concurrency and safety". Decision D5: the orchestrator, not ingest, uploads the ZIP.
