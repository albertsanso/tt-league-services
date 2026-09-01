# Build a frontend feature

Implement the requested feature in `tt-data-league-frontend`.

Follow these sources in order:

- `tt-data-league-frontend/AGENTS.md`
- `docs/frontend/design-contract.md`
- `docs/frontend/theme-spec.md`
- Existing route, navigation, layout, hook, API, i18n, and UI primitive
  conventions

Before editing, inspect the affected page and its surrounding shared code.
Reuse existing primitives from `src/components/ui` and CSS variables from
`src/index.css`. Do not introduce arbitrary colours, decorative gradients,
oversized rounded corners, large shadows, duplicate controls, or literal
user-facing strings.

Implement intentional loading, success, empty, error, disabled, desktop,
tablet, mobile, keyboard, focus, and reduced-motion behavior where applicable.
Use semantic HTML and accessible names. Add translation keys to Catalan,
Spanish, and English resources. Keep API normalization at the API or hook
boundary and preserve lazy routing and existing provider boundaries.

After implementation, review the diff against
`docs/frontend/design-contract.md` and run the existing frontend lint, build,
and focused tests.

Feature request:

> [Describe the feature, route, data, and acceptance criteria here.]
