# Build Plan

> Draft outline.

## Acceptance Criteria

- [ ] Operators can replay the import of a past run from the API and the runs view; it creates a `RETRY` run linked to the original
- [ ] Artifacts follow a configurable retention policy (for example ZIPs for the season, raw files 90 days), enforced by a cleanup job
- [ ] Replaying an unchanged ZIP returns the existing import job (idempotency) and the run records that
- [ ] Tests cover replay, retention and the idempotent case

# Implementation Guidelines

- Re-parsing stored raw files needs an ingest "parse only from stored content" option; out of scope unless added to the ingest service.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Audit and replay".
