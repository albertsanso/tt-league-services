# SDD agent instructions

These rules apply to feature-planning files under `docs/sdd/`. Follow the
repository or module `AGENTS.md` files for application code.

## Source of truth

- `FEATURES.md` is the authoritative feature registry.
- `task-management.md` defines the registry template and statuses.
- `FEAT-XXXXX-DETAILS.md` contains one feature's plan, guidelines, and notes.

Use unique headings `### [FEAT-XXXXX] Title`, matching details filenames and
relative links. IDs are five-digit zero-padded values; never reuse an ID.
Preserve the existing Markdown structure.

## Status workflow

Use only: `idea`, `planned`, `ready`, `in-progress`, `in-review`, `done`, or
`blocked`.

- `idea`, `planned`, `ready`, and `blocked` belong under **Backlog**.
- `in-progress` belongs under **In Progress**.
- `in-review` belongs under **In Review** after implementation is finalized.
- `done` belongs under **Done**, sorted by descending feature ID.

When changing status, move the complete feature block. Keep acceptance
criteria and details synchronized with reality. Document blockers in the
details file's `# Notes`. Only change `in-review` to `done` when the user
explicitly requests that transition.

## Agent boundaries

- Ordinary implementation work starts only from `ready`.
- SDD maintenance may update statuses and plans when explicitly requested.
- Do not change `done` or `in-progress` entries during ordinary code work.
- Never delete features or invent statuses without explicit instruction.
  Archiving is not deletion; see below.
- Preserve historical notes; append decisions rather than silently removing
  them.

## Archiving done features

Only on an explicit request (e.g. "archive current features"), move every
`done` feature out of `FEATURES.md` into `docs/sdd/archive/<YYYY-MM-DD>/` with:

```text
python .github/skills/feature-management/scripts/feature_manager.py archive [--date YYYY-MM-DD] [--dry-run]
```

The helper moves each done feature's details file, writes
`FEATURES-done.archive.<date>.md` with its index entry and registry block, and
removes both from `FEATURES.md`. Other sections are untouched. Do not archive
by hand-editing `FEATURES.md`. Archived IDs stay reserved and must never be
reused. Afterwards, review the diff and run `validate`.

Before finishing, confirm the status, section, ID/link, build plan, blockers,
acceptance criteria, and `Done` ordering are correct.
