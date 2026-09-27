 dev# FEATURES.md — Feature Registry & Build Plans

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

- [FEAT-00074: FCTT import resource format modified](### [FEAT-00074] FCTT import resource format modified)

## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

---
## Done

### [FEAT-00074] FCTT import resource format modified
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** —

#### Goal
Female leagues are included in the FCTT import process, and the import process should be updated to handle these changes.

#### Description
The FCCT ZIP resource file structure has been modified to include female leagues. 
The import process needs to be updated to accommodate `male`/`female` in the folder structure, and the system should be able to correctly identify and process both league types.

Current folder structure:
```
├── tt-repository
│   ├── import-fctt
│   │   ├── actas
│   │   │   ├── 2025-2026
│   │   │   │   ├── tercera nacional
│   │   │   │   │   ├── G1
│   │   │   │   │   ├── G2
│   │   │   │   │   ├── G3
```

Target folder structure:
```
├── tt-repository
│   ├── import-fctt
│   │   ├── actas
│   │   │   ├── 2026-2027
│   │   │   │   ├── male
│   │   │   │   │   ├── tercera nacional
│   │   │   │   │   │   ├── G1
│   │   │   │   │   │   ├── G2
│   │   │   │   │   │   ├── G3
│   │   │   │   ├── female
│   │   │   │   │   ├── copa-catalana-femenina-1a
│   │   │   │   │   ├── copa-catalana-femenina-2a
```

#### Acceptance Criteria
- [x] The import process correctly identifies and processes both `male` and `female` league types from the `<season>/<male|female>/<competition>/[<group>/]` layout; unknown gender folders are logged and skipped.
- [x] Imported data keeps league types distinct: FCTT competitions are stored as `<competition>-masculino` / `<competition>-femenino`, and group-less female competitions are stored with no group number.
- [x] New-format file names (`jornada-<d>-partido-<m>.json` and `jornada-<d>-partido-<home>-<away>.json`) are imported.
- [x] Unpublished actas (`acta_publicada: false`) are not stored as played matches, and their placeholder results are never persisted.
- [x] The payload `fase` is stored as the match phase and is part of the match natural key; re-importing stays idempotent.
- [x] Import and preview follow `docs/acta-model-definition.json`: optional `id_partido`/`acta_publicada`/`genero` are parsed, a nullable `abc_es_local` and empty `partidos`/`alineaciones` in unpublished actas are handled, and the schema documents that unpublished `resultado_final` is a placeholder.
- [x] The import process is tested with sample data for both male and female leagues.

#### Feature Details
→ See [FEAT-00074-DETAILS.md](./FEAT-00074-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---
