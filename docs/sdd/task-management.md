# SDD agent guide — feature registry lifecycle

This document tells AI agents how to maintain the software-design documents under `docs/sdd/`, especially:

- [`FEATURES.md`](./FEATURES.md) — feature registry (status, sections, links)
- [`FEAT-*-DETAILS.md`](./) — per-feature build plans and notes

This file governs **requirements and planning artifacts only**.

---

## Scope and precedence

| Artifact | Purpose |
|----------|---------|
| `FEATURES.md` | Single registry: what exists, its status, priority, and a pointer to details |
| `FEAT-NNN-DETAILS.md` | Deep dive: build plan steps, implementation guidelines, notes |

If `FEATURES.md` and a details file disagree, **`FEATURES.md` wins** for status, priority, and acceptance criteria unless a human explicitly reconciles them.

## Feature identity and filenames

- Registry entries use the heading form: `### [FEAT-NNN] Short title` (three-digit zero-padded ID, e.g. `FEAT-001`).
- Details file name must match: `FEAT-NNN-DETAILS.md` in the same directory as `FEATURES.md`.
- Link from the registry using a relative path: `[FEAT-NNN-DETAILS.md](./FEAT-NNN-DETAILS.md)`.
- When adding a **new** feature, pick the next unused `NNN` (scan existing headings and `FEAT-*-DETAILS.md` files; do not reuse IDs).

---

## Status values

Use **only** these literals in `- **Status:** …` (lowercase, hyphenated where shown):

| Status | Meaning |
|--------|---------|
| `idea` | Captured; no committed build plan in the details file yet |
| `planned` | Build plan drafted in `FEAT-NNN-DETAILS.md` |
| `ready` | Plan is approved for implementation; code work may start (see root `AGENTS.md`) |
| `in-progress` | Implementation underway |
| `done` | Shipped; registry entry is closed |
| `blocked` | Waiting on dependency or decision; document the blocker in the details file **Notes** |

Do not invent alternate labels (e.g. “In Progress” with a space) in the YAML-style line; the registry uses the table above.

---

## Section placement in `FEATURES.md`

The file has three feature sections (in this **top-to-bottom order**):

1. `## In Progress`
2. `## Backlog`
3. `## Done`

**Rule:** Each feature block (from `### [FEAT-NNN]` through the closing `---` before the next heading or section) must live under **exactly one** of these sections, determined by **Status**:

| Status | Section |
|--------|---------|
| `in-progress` | **In Progress** |
| `done` | **Done** |
| `idea`, `planned`, `ready`, `blocked` | **Backlog** |

When you change a feature’s status, **move the entire block** (heading, metadata, goal, acceptance criteria, details link, and any subsections) to the correct section. Keep sections ordered: **In Progress** first, then **Backlog**, then **Done**.

**Ordering within a section:**

- **`## Done`:** Sort feature blocks by **`FEAT-NNN` id in descending alphabetical order** (lexicographic descending on the full id string, e.g. `FEAT-008` above `FEAT-007`, … down to `FEAT-001`). When moving a feature to **Done**, insert it in this order (do not append only at the top or bottom unless that happens to match the sort).
- **In Progress** and **Backlog:** No strict rule unless the human requests it; default to chronological (newest items near the top or bottom consistently with surrounding entries—match the prevailing pattern in the file).

---

## Lifecycle workflows (agent actions)

### 1. New feature (`idea`)

1. Append (or place per file convention) a new `### [FEAT-NNN]` block under **Backlog** using the template in `FEATURES.md`.
2. Set `- **Status:** idea`.
3. Either add a stub `FEAT-NNN-DETAILS.md` with placeholder headings (`# Build Plan`, `# Implementation Guidelines`, `# Notes`) or omit the file until moving to `planned`—but the registry link should still point to `./FEAT-NNN-DETAILS.md` when the file exists.
4. Fill **Goal** and initial **Acceptance Criteria**; use `Depends on:` for other `FEAT-NNN` IDs or external deps.

### 2. Plan written (`planned`)

1. Create or update `FEAT-NNN-DETAILS.md` with a concrete **Build Plan** (numbered steps, file paths, contracts as needed).
2. Set status to `planned` in `FEATURES.md`.
3. Keep the feature under **Backlog** (not `in-progress` / **Done**).

### 3. Ready for implementation (`ready`)

1. Ensure the build plan is complete enough for an implementer (root `AGENTS.md`: work on features marked `ready`).
2. Set status to `ready` in `FEATURES.md`.
3. Remain under **Backlog** until someone marks implementation started.

### 4. Implementation started (`in-progress`)

1. Set status to `in-progress`.
2. Move the whole feature block to **In Progress**.
3. Update `FEAT-NNN-DETAILS.md` if the plan changes (see [Editing details](#editing-details)).

### 5. Shipped (`done`)

1. Set status to `done`.
2. Move the block to **Done**, placing it in **descending `FEAT-NNN` order** (see [Section placement](#section-placement-in-featuresmd) — **Done** sorting rule).
3. Align acceptance checkboxes in `FEATURES.md` with reality (check completed items).
4. Add a short **Notes** or “Shipped” line in details if useful; avoid large rewrites unless requested.

### 6. Blocked (`blocked`)

1. Set status to `blocked`.
2. Keep the block under **Backlog** (blocked is not “in progress” unless the team explicitly wants it in **In Progress**—**default: Backlog**).
3. In `FEAT-NNN-DETAILS.md`, under **Notes**, state what blocks progress and what unblocks it.

---

## Editing details (`FEAT-*-DETAILS.md`)

**Create** the file when:

- Status moves to `planned` or earlier if the human wants a running doc; or
- You split a large feature and need a place for steps—still one file per `FEAT-NNN`.

**Recommended structure** (adapt to existing files):

```markdown
# Build Plan
(Numbered steps, paths, APIs.)

# Implementation Guidelines
(Coding constraints, patterns, out-of-scope.)

# Notes
(Open questions, links, env vars, blockers.)
```

**Modify** the details file when:

- Build steps change during `planned` / `ready` refinement.
- Implementation discovers necessary plan updates while `in-progress` (keep plan and reality in sync).
- Blockers, decisions, or follow-ups need recording (`blocked` or any status).

**Do not** remove historical decisions from **Notes** without explicit instruction; append clarifications instead.

---

## Constraints (align with `FEATURES.md`)

- **Implementation agents:** The banner in `FEATURES.md` applies to **code** work: prefer working on features at `ready`; do not change `done` or `in-progress` registry entries **unless** the user asked for doc or scope updates.
- **SDD maintenance agents:** You **may** edit statuses, move sections, and update details files **when the user asks** or when the task is explicitly to maintain SDD artifacts.
- Never delete a feature from the registry without explicit instruction; prefer `done` or `blocked` with explanation.
- Preserve the existing markdown rhythm in `FEATURES.md` (`---` separators, `####` subheadings) unless a human requests a format change.

---

## Quick checklist

- [ ] Status is one of the six allowed values.
- [ ] Feature block sits under **In Progress**, **Backlog**, or **Done** according to the table above.
- [ ] **`## Done`** entries are ordered by **`FEAT-NNN` descending** (alphabetical descending on the id).
- [ ] `FEAT-NNN` in the heading matches `FEAT-NNN-DETAILS.md`.
- [ ] Details file has a **Build Plan** before or when status is `planned` or later.
- [ ] Blockers documented in details **Notes** when `blocked`.

