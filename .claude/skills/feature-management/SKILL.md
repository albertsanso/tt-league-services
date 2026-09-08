---
name: feature-management
description: Create, plan, update, execute, and close SDD features under docs/sdd/, keeping FEATURES.md and FEAT-XXXXX-DETAILS.md files synchronized.
---

# Feature management

This skill is a pointer to the authoritative feature-lifecycle skill kept at
`.github/skills/feature-management/SKILL.md`. That file (not this one) owns
the full workflow: registry conventions, the `feature_manager.py` helper
usage, plan creation/rebuild rules, execution/status transitions, and the
final checklist.

Read `.github/skills/feature-management/SKILL.md` in full and follow it
exactly for any work on `docs/sdd/FEATURES.md` or `docs/sdd/FEAT-XXXXX-DETAILS.md`
files. Use the deterministic helper at
`.github/skills/feature-management/scripts/feature_manager.py` for registry
mutations, as documented there.

Do not duplicate or fork the lifecycle rules into this file — if the workflow
needs to change, update `.github/skills/feature-management/SKILL.md` so both
GitHub-side automation and Claude Code stay on one source of truth.
