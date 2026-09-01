# TT League frontend design contract

This is the compact implementation contract for new frontend features.
`theme-spec.md` remains the complete visual reference; this file contains the
rules that should be easy for developers, reviewers, and coding agents to
apply consistently.

## Visual language

- Use the light institutional dashboard style: `--surface-app` for the page,
  `--surface-card` for cards, and `--surface-sidebar` only for the sidebar.
- Use `--accent-primary` for primary actions, active navigation, emphasis, and
  focus rings. Use `--secondary` only for secondary interactive or informative
  elements.
- Use `Inter` for interface text. Use `DM Mono` only for numerical statistics.
- Prefer hierarchy through typography, spacing, and thin borders.
- Use `border-radius: 0.5rem` or smaller. Use no decorative gradients,
  illustrations, emoji, multicolour section backgrounds, or large shadows.
- Use `shadow-sm` only for an interaction state such as card hover.

## Component rules

- Reuse components from `src/components/ui` before creating local equivalents.
- Use `SectionLabel` for section headings and the shared `Card`, `Button`,
  `Input`, and state components for their respective patterns.
- Keep page-specific composition in `src/pages` or a feature directory.
- Keep individual components below 150 lines where practical.
- Use CSS variables from `src/index.css`; do not add arbitrary colour values
  for a feature.

## Responsive and accessible behavior

- Preserve the desktop (at least 1280px), tablet (768px to 1279px), and mobile
  (below 768px) layouts.
- Every interactive control needs a semantic element, accessible name, visible
  focus state, and keyboard behavior.
- Implement loading, empty, error, disabled, and success states where they can
  occur. Expose asynchronous status with an appropriate live region.
- Respect `prefers-reduced-motion`.
- Use the existing i18n resources for all user-facing text in Catalan,
  Spanish, and English.

## Review checklist

- [ ] Existing UI primitives and design tokens are reused.
- [ ] No hard-coded feature colours, gradients, oversized radii, or decorative
      shadows were introduced.
- [ ] Desktop, tablet, and mobile layouts are intentional.
- [ ] Loading, empty, error, disabled, and success states are covered.
- [ ] Keyboard access, accessible names, focus, contrast, and reduced motion
      are covered.
- [ ] Translation keys exist in all supported resources.
- [ ] Route metadata, navigation, lazy loading, and API boundaries follow the
      frontend module instructions.
