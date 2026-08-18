# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template below.
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `done` | Shipped |
| `blocked` | Waiting on a dependency or decision |

---

## Template

Copy this block to add a new feature:

```
### [FEAT-000] Feature Name
- **Status:** idea
- **Priority:** low | medium | high
- **Effort:** small (< 2h) | medium (2–8h) | large (> 8h)
- **Depends on:** —

#### Goal
One sentence: what problem does this solve for the user?

#### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

#### Feature Details
→ See [FEAT-000-DETAILS.md](./FEAT-000-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.
```

### Feature Details file format
```
# Build Plan
> Fill this in when status moves to `planned`.

1. Step 1
2. Step 2
...

# Implementation Guidelines

# Notes
Any open questions, design decisions, or links.
```

## In Progress

## Backlog

## Done

---