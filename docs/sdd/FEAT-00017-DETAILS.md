# Build Plan

1. Add and configure `i18next` and `react-i18next` in
   `tt-data-league-frontend`, with Catalan (`ca`) as the default locale and
   Catalan, Spanish (`es`), and English (`en`) as the initial supported
   languages.
2. Create translation resources for each supported language under
   `tt-data-league-frontend/src/i18n/`, using stable semantic keys and
   interpolation for dynamic labels.
3. Mount the i18n provider/initialization at the application root in
   `tt-data-league-frontend/src/main.jsx` without changing the existing router,
   authentication, or app-state provider boundaries.
4. Configure locale persistence in browser storage, validate stored locale
   values, and fall back deterministically to Catalan when the value is
   missing or invalid.
5. Add an accessible language selector to
   `tt-data-league-frontend/src/pages/SettingsPage.jsx`; switching language
   updates visible copy immediately without reloading or changing the current
   route.
6. Replace current user-facing literal strings with translation keys across
   the application shell, navigation, route metadata and breadcrumbs,
   authentication pages, search pages, detail pages, shared components,
   loading states, empty states, errors, and accessibility labels. Keep API
   values, club/player names, and source data unchanged.
7. Add focused frontend tests for initialization and persistence, language
   switching, missing-key fallback, interpolation, translated
   navigation/breadcrumbs, and preserving the current route and UI state after
   switching languages.
8. Document supported languages, locale selection, translation-key
   conventions, fallback behavior, and the process for adding another
   language. Run the existing frontend test, lint, and production-build
   commands.

# Implementation Guidelines

- Keep translation resources and locale state in the existing React
  application boundary; do not introduce a second global state mechanism.
- Preserve the current Catalan copy and existing loading, empty, error,
  responsive, and accessibility behavior while translating it.
- Do not translate backend identifiers or user-provided names. Use
  locale-aware formatting for dates, numbers, and percentages only where the
  current UI already presents those values.
- Do not change REST contracts, route paths, permissions, authentication
  behavior, or backend modules.
- Keep language codes and resource keys explicit and extensible so additional
  locales can be added without changing component behavior.

# Notes

- Initial scope is frontend UI localization for Catalan, Spanish, and English.
- The registry acceptance criteria require all current user-facing literal
  strings to be migrated to translation resources.
- Implemented the planned localization scope and moved the feature to
  `in-review`; locale resources, persistence behavior, selector behavior, and
  frontend validation are documented in the frontend README.
- Shipped and closed by explicit user approval on 2026-08-30.
