# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template in [`task-management.md`](./task-management.md).
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `in-review` | Implementation finalized and awaiting user review |
| `done` | Shipped after explicit user approval |
| `blocked` | Waiting on a dependency or decision |

---

## Main index

- [FEAT-00095: New Workspace module for results and matches data ingestion](### [FEAT-00095] New Workspace module for results and matches data ingestion)

## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00095] New Workspace module for results and matches data ingestion
- **Status:** done
- **Priority:** medium
- **Effort:** large
- **Depends on:** —

#### Goal
Provide a new Workspace python module that ingests league results and match data from source files into the platform, complementing the Java import pipeline.

#### Description
The Workspace module is named `tt-league-ingest` with the related `pyproject.toml` and `uv.lock` files at repository root level and is intended to be used as a standalone ingestion module for league results and match data.
It is a standalone module that can be installed and run independently of the platform, but it is designed to be used in conjunction with the platform's ingestion pipeline.

This `tt-league-ingest` module contains:
- Workspace submodules for each Python based federation source extraction (e.g., `tt-league-ingest-rfetm`, `tt-league-ingest-bcnesa`, `tt-league-ingest-fctt`) that handle the specific data formats and ingestion logic for each federation.
- Workspace submodules for Python based common ingestion logic and utilities (e.g., `tt-league-ingest-common`) that can be shared across federation modules. JSON schema artifact.
- Workspace submodule for Python based runtime CLI (e.g., `tt-league-ingest-cli`) that provides a command-line interface for running the ingestion process.
- Workspace submodule for Python based runtime Rest API (e.g., `tt-league-ingest-rest`) that provides a RESTful API for triggering ingestion and monitoring progress.

The folder structure of the `tt-league-ingest` module is as follows:
```
tt-league-ingest/
├── pyproject.toml
├── uv.lock
├── README.md
└── packages/
    └── tt-league-ingest-rfetm/
        ├── pyproject.toml
        └── src/ingest_rfetm/__init__.py
    └── tt-league-ingest-bcnesa/
        ├── pyproject.toml
        └── src/ingest_bcnesa/__init__.py
    └── tt-league-ingest-fctt/
        ├── pyproject.toml
        └── src/ingest_fctt/__init__.py
    └── tt-league-ingest-common/
        ├── pyproject.toml
        └── src/ingest_common/__init__.py
    └── tt-league-ingest-cli/
        ├── pyproject.toml
        └── src/ingest_cli/__init__.py
    └── tt-league-ingest-rest/
        ├── pyproject.toml
        └── src/ingest_rest/__init__.py
```

The root pyproject.toml declares the modules:

```toml
[tool.uv.workspace]
members = ["packages/*"]
```

A submodule that depends on another one, like packages/tt-league-ingest-rest/pyproject.toml:

```toml
[project]
name = "api"
version = "0.1.0"
dependencies = ["ingest_rest"]

[tool.uv.sources]
core = { workspace = true }
```

#### Acceptance Criteria
- [x] The `tt-league-ingest` module is created with the specified folder structure and submodules.
- [x] Each submodule has its own `pyproject.toml` and `__init__.py` files.
- [x] The root `pyproject.toml` correctly declares the workspace members.
- [x] The ingestion logic for each federation is implemented in the respective submodules.
- [x] The common ingestion logic and utilities are implemented in the `tt-league-ingest-common` submodule.
- [x] The CLI and REST API for triggering ingestion and monitoring progress are implemented in the respective submodules.
- [x] All submodules are correctly integrated and can be built and run using the workspace tooling.
- [x] The workspace tooling correctly resolves dependencies and allows running commands in specific submodules.
- [x] All submodules have been tested and verified to work as expected.

#### Feature Details
→ See [FEAT-00095-DETAILS.md](./FEAT-00095-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---
