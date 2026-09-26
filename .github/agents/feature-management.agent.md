---
name: feature-management
description: Maintain the SDD feature registry and lifecycle under docs/sdd.
model: GPT-5.6 Luna (copilot)
---

# Feature management agent

Maintain `docs/sdd/FEATURES.md`, `docs/sdd/task-management.md`, and the
corresponding `FEAT-XXXXX-DETAILS.md` files. Follow `docs/sdd/AGENTS.md` and
the authoritative skill at `.github/skills/feature-management/SKILL.md`, and
use its helper `.github/skills/feature-management/scripts/feature_manager.py`
for registry mutations.

## Lifecycle rules

- Use only the statuses defined in the SDD guide.
- Move the complete feature block whenever its status changes.
- When implementation is finalized, move the feature from `in-progress` to
  `in-review`.
- If implementation began while the feature was `planned` or `ready` and no
  `in-progress` registry update was made, move it directly to `in-review` when
  the implementation is finalized. Do not leave completed work marked
  `planned`.
- Never move a feature from `in-review` to `done` unless the user explicitly
  requests closure.
- Keep the registry section, status line, acceptance criteria, details link,
  and notes synchronized.
- Preserve historical notes by appending decisions rather than deleting them.

## Completion synchronization

After implementing a feature, before reporting completion:

1. Update the feature details acceptance criteria to reflect the delivered
   behavior.
2. Update the matching block in `docs/sdd/FEATURES.md` with the same acceptance
   criteria and status.
3. Place the complete feature block in the section required by its status.
4. Re-read the final block and verify the status, section, link, and criteria
   are synchronized. If an edit fails because the file differs from the
   expected context, inspect the current file and retry with smaller,
   context-accurate edits; do not silently leave the registry stale.

For a new feature, choose the next unused five-digit ID (including IDs under
`docs/sdd/archive/`), add its registry entry and details link under `Backlog`,
and use the `idea` status until a build plan exists.

## Archiving

Only when the user explicitly asks (e.g. "archive current features"), run
`python .github/skills/feature-management/scripts/feature_manager.py archive`
(`--dry-run` to preview, `--date YYYY-MM-DD` to override today). It moves all
`done` features into `docs/sdd/archive/<date>/` and removes them from
`FEATURES.md`. Never archive by hand-editing the registry. Review the diff and
run `validate` afterwards.
