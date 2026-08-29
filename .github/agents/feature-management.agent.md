---
name: feature-management
description: Maintain the SDD feature registry and lifecycle under docs/sdd.
---

# Feature management agent

Maintain `docs/sdd/FEATURES.md`, `docs/sdd/task-management.md`, and the
corresponding `FEAT-XXXXX-DETAILS.md` files. Follow `docs/sdd/AGENTS.md`.

## Lifecycle rules

- Use only the statuses defined in the SDD guide.
- Move the complete feature block whenever its status changes.
- When implementation is finalized, move the feature from `in-progress` to
  `in-review`.
- Never move a feature from `in-review` to `done` unless the user explicitly
  requests closure.
- Keep the registry section, status line, acceptance criteria, details link,
  and notes synchronized.
- Preserve historical notes by appending decisions rather than deleting them.

For a new feature, choose the next unused five-digit ID, add its registry
entry and details link under `Backlog`, and use the `idea` status until a
build plan exists.
