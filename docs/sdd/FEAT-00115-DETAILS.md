# Build Plan

> Draft outline.

## Acceptance Criteria

- [ ] Micrometer metrics (runs by outcome, step durations, pending matches, open match days) are exposed through Actuator in Prometheus format
- [ ] The orchestrator logs in structured JSON with `runId`, and passes its `runId` to the ingest service, which includes it on every log line of that run
- [ ] Tests check metric registration and run-id propagation

# Implementation Guidelines

- Grafana boards and the log stack are deployment concerns, documented but not committed as environment-specific config.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Operational observability".
