# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-core`: `TriggerRun` use case shared with the scheduler.
2. `tt-league-pipeline-orchestrator-runtime`: Spring Security resource-server setup for HS256 platform JWTs (secret from the environment),
   controllers, DTOs, OpenAPI, SSE emitter fed by run-state events.
3. Tests; README.

## Acceptance Criteria

- [ ] `POST /api/pipeline/runs` (`source` or `ALL`, scope `OPEN_MATCH_DAYS`/`GROUP`/`FULL_SEASON`, `force`) creates `MANUAL` runs recording the user id
- [ ] A trigger for a source with an active run is rejected with 409 and a clear message (configurable to queue instead)
- [ ] `GET /api/pipeline/runs` (filters: source, status, from/to; paged) and `GET /api/pipeline/runs/{id}` return runs with steps, durations, issues and import report
- [ ] `GET /api/pipeline/events` streams run and step transitions as Server-Sent Events
- [ ] Platform JWTs are validated; viewing needs authentication and triggering needs `matches:write`
- [ ] Controller and security tests cover validation, 409, permissions and the event stream

# Implementation Guidelines

- Manual and scheduled runs share one code path and one queue (proposal "Run manager").

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Decision D7 (auth). `ALL` creates one run per source.
