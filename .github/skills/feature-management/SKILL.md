---
name: feature-management
description: Create, plan, update, execute, and close SDD features while keeping the registry and details synchronized.
---

# Feature management

Use this skill for work on the software-design documents under `docs/sdd/`.
This skill is the authoritative lifecycle guidance. The feature artifacts are:

- `docs/sdd/FEATURES.md`: the feature registry and status source of truth.
- `docs/sdd/FEAT-XXXXX-DETAILS.md`: the plan, implementation guidance, and notes
  for one feature.

Follow the repository `AGENTS.md` and any applicable module-level `AGENTS.md`
for application-code changes. This skill changes planning artifacts only unless
the user explicitly requests implementation work.

## Deterministic helper

Use the dependency-free Python helper at
`.github/skills/feature-management/scripts/feature_manager.py` for registry
mutations and validation. Run it from the repository root, or provide
`--root <path>`:

```text
python .github/skills/feature-management/scripts/feature_manager.py validate
python .github/skills/feature-management/scripts/feature_manager.py create --title "..." --goal "..." --acceptance "..."
python .github/skills/feature-management/scripts/feature_manager.py update --id FEAT-XXXXX --goal "..."
python .github/skills/feature-management/scripts/feature_manager.py plan --id FEAT-XXXXX --file plan.md --mark-planned
python .github/skills/feature-management/scripts/feature_manager.py status --id FEAT-XXXXX --status in-progress
python .github/skills/feature-management/scripts/feature_manager.py status --id FEAT-XXXXX --status blocked --note "..."
python .github/skills/feature-management/scripts/feature_manager.py status --id FEAT-XXXXX --status done --confirm-done --check-acceptance
```

The helper allocates the next unused ID, updates the complete registry block
when moving status, preserves notes, keeps **Done** sorted, requires explicit
confirmation for `done`, rejects unchecked acceptance criteria at closure,
replaces only the `# Build Plan` section for plan rebuilds, and validates
registry/details invariants. `--check-acceptance` is required when closing a
feature whose registry criteria are still unchecked. Review the diff after every mutation; use manual
edits for prose or plan content that the helper does not model.

## Registry conventions and invariants

- Use a unique, five-digit, zero-padded `FEAT-XXXXX` ID. Never reuse an ID.
- Use only these status values: `idea`, `planned`, `ready`, `in-progress`,
  `in-review`, `done`, and `blocked`.
- Keep every feature block in the section implied by its status:
  `in-progress` → **In Progress**, `in-review` → **In Review**, `done` →
  **Done**, and all other statuses → **Backlog**.
- Keep the registry heading, metadata, goal, acceptance criteria, details link,
  status, and notes synchronized.
- Preserve historical decisions and notes by appending; do not silently delete
  them.
- Do not delete features or invent statuses without explicit instruction.
- Never change `in-review` to `done` without an explicit user request.
- Keep **Done** sorted by descending feature ID.

`FEATURES.md` is the single registry for feature identity, status, priority,
acceptance criteria, and links. Each feature uses a matching
`FEAT-XXXXX-DETAILS.md` file in the same directory. Registry entries use the
heading form `### [FEAT-XXXXX] Short title` and a relative details link.

Use this template for new entries under `## Backlog`:

```markdown
### [FEAT-00000] Feature Name
- **Status:** idea
- **Priority:** low | medium | high
- **Effort:** small (< 2h) | medium (2-8h) | large (> 8h)
- **Depends on:** —

#### Goal
One sentence describing the user problem solved.

#### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

#### Feature Details
-> See [FEAT-00000-DETAILS.md](./FEAT-00000-DETAILS.md) for the detailed
build plan and implementation notes.
```

Details files use `# Build Plan`, `# Implementation Guidelines`, and `# Notes`.
A concrete numbered build plan is required for `planned` and later statuses.
Record blockers, decisions, and follow-ups under `# Notes`.

## General workflow

1. Read the relevant feature block, its details file, the repository/module
   guidance, and the current `git diff`.
2. Confirm the feature ID, current status, intended operation, dependencies, and
   acceptance criteria before editing.
3. Edit the details file and registry as one synchronized change. Move the
   complete registry block when its status changes; do not edit only the status
   line in place.
4. Re-read the changed block and details file. Verify status, section, ID,
   relative link, plan, acceptance criteria, dependencies, and notes.
5. Review the final diff for accidental files or unrelated changes.

## Creating a feature

When creating a feature:

1. Scan `FEATURES.md` headings and existing `FEAT-*-DETAILS.md` files for the
   next unused ID.
2. Add a complete registry block under **Backlog** using status `idea`.
3. Add the feature to `## Main index`, matching the existing link style.
4. Set a concise goal, initial acceptance criteria, priority, effort, and
   dependencies. Use `—` when there are no dependencies.
5. Create `FEAT-XXXXX-DETAILS.md` when a running document is useful, using
   `# Build Plan`, `# Implementation Guidelines`, and `# Notes`. A build plan
   is required before moving to `planned`.

Do not mark a newly captured feature `ready` or `in-progress`; those statuses
require a complete plan and an explicitly approved implementation transition.

## Updating or modifying a feature

For scope, acceptance-criteria, priority, dependency, or plan changes:

- Update both the matching registry block and details file where the information
  is represented.
- Keep acceptance criteria identical in meaning between the two files.
- Update the build plan when steps, paths, contracts, or dependencies change.
- Append decisions, blockers, and follow-ups under `# Notes`; retain prior
  historical notes.
- If a status changes, move the entire block to the required section.

If the file differs from expected context, inspect the current content and make
a smaller context-accurate edit. Never leave one side of the registry/details
pair stale after an edit failure.

## Creating or rebuilding a plan

### Creating a plan

To move `idea` to `planned`:

1. Write a concrete numbered `# Build Plan` in `FEAT-XXXXX-DETAILS.md`.
2. Include affected files/modules, interfaces or contracts, implementation
   order, tests, documentation, and relevant integration points.
3. Record constraints and out-of-scope behavior under
   `# Implementation Guidelines`.
4. Record open questions or dependencies under `# Notes`.
5. Change the registry status to `planned`; keep the block under **Backlog**.

### Rebuilding a plan

When a plan is incomplete, stale, or the user asks for a rebuild:

1. Compare the plan with the current repository structure, APIs, tests, and
   applicable module guidance.
2. Preserve still-valid steps and historical decisions.
3. Replace or reorder obsolete steps with an actionable implementation sequence.
4. Update affected acceptance criteria only when the intended behavior changed.
5. Keep the feature `planned` unless the user explicitly approves it as
   `ready`; keep `ready` only when the plan is implementation-ready.
6. Note material changes and their rationale under `# Notes`.

A plan is ready for implementation only when an implementer can follow it
without resolving unspecified file ownership, contracts, dependencies, or
acceptance behavior.

## Executing a plan

Plan execution means coordinating the feature lifecycle and, when requested,
handing the plan to an implementation agent:

1. Confirm the feature is `ready` before ordinary implementation begins.
2. When implementation starts, change the status to `in-progress` and move the
   complete block to **In Progress**. Do this only when the transition is
   requested or implementation has actually started.
3. Keep the details plan and notes synchronized with discoveries during work.
4. When implementation is finalized, verify each acceptance criterion against
   the delivered behavior, record relevant validation notes, change the status
   to `in-review`, and move the complete block to **In Review**.
5. Do not mark the feature `done` merely because implementation or tests
   finished. Require the user's explicit closure request.

If implementation began from `planned` or `ready` without an intermediate
`in-progress` update, move it directly to `in-review` when finalized; never
leave completed work marked `planned` or `ready`.

## Managing feature state

Use these transitions and placement rules:

| Transition | Required action |
|---|---|
| New → `idea` | Add registry block and index entry under **Backlog**. |
| `idea` → `planned` | Add a concrete details build plan; remain in **Backlog**. |
| `planned` → `ready` | Confirm the plan is approved and implementation-ready; remain in **Backlog**. |
| `ready` → `in-progress` | Move the complete block to **In Progress**. |
| `in-progress` → `in-review` | Verify acceptance criteria and move the complete block to **In Review**. |
| `in-review` → `done` | Only on explicit user request; check criteria and insert in descending ID order under **Done**. |
| Any active state → `blocked` | Move to **Backlog** and document the blocker and unblock condition in `# Notes`. |

Ordinary implementation agents should work only on `ready` features and should
not alter `done`, `in-progress`, or `in-review` registry entries unless the
user explicitly requests SDD maintenance or scope updates.

## Final checklist

- [ ] ID is unique, five-digit, and consistent with the details filename.
- [ ] Registry entry appears in exactly one status section.
- [ ] Status is allowed and matches section placement.
- [ ] `## Main index` contains the feature.
- [ ] Registry and details acceptance criteria are synchronized.
- [ ] A concrete build plan exists for `planned` and later states.
- [ ] Blockers are documented for `blocked`.
- [ ] **Done** remains in descending feature-ID order.
- [ ] Historical notes and unrelated work are preserved.
