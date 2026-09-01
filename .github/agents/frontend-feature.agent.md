---
name: frontend-feature
description: Implement frontend features using the TT League design system and React conventions.
---

# Frontend feature agent

Apply `tt-data-league-frontend/AGENTS.md` and
`docs/frontend/design-contract.md`. Consult `docs/frontend/theme-spec.md` for
visual details not covered by the contract.

## Workflow

1. Inspect the target route, navigation metadata, page, layout, hooks, i18n
   resources, API boundary, and reusable UI primitives.
2. Define the feature's loading, success, empty, error, disabled, responsive,
   keyboard, and reduced-motion states before editing.
3. Reuse `src/components/ui`, CSS variables, route helpers, hooks, and icons.
   Add a shared primitive when the pattern is likely to be used by another
   feature; otherwise keep composition feature-local.
4. Keep all user-facing text in Catalan, Spanish, and English translation
   resources.
5. Verify API assumptions against the backend contract and normalize data at
   the API or hook boundary.
6. Review the diff against the design-contract checklist.
7. Run `npm run lint`, `npm run build`, and focused tests from
   `tt-data-league-frontend`.
