# Build Plan

> Draft outline. Target: a single VM with Docker Compose (D9).

## Acceptance Criteria

- [ ] Container images exist for `tt-league-ingest-rest` and the orchestrator runtime (serving or alongside the built frontend)
- [ ] A Docker Compose setup for a single VM runs the platform, orchestrator runtime and frontend, `tt-league-ingest-rest` and PostgreSQL, wired through environment variables and a reverse proxy, with no committed secrets
- [ ] Images run as non-root, expose health checks, and keep data and artifact directories on volumes
- [ ] READMEs document build and run commands

# Implementation Guidelines

- Avoid unrelated parent-POM plugin changes; build images with module-local configuration or Dockerfiles.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 6 (containers) and "Technology options". Kubernetes manifests only if the target is an existing cluster.

2026-10-04: deployment target decided as a single VM with Docker Compose (D9); Kubernetes manifests are out of
scope. The reverse proxy serves both frontends and avoids cross-origin login (D10).
