# Build Plan

1. Extend `tt-data-league-frontend/src/config/routes.js` with centralized Administration paths and route metadata for the administrator-only parent destination and the Users and Roles, System Settings, and Data Import suboptions. Add breadcrumb labels and permission/role metadata without duplicating paths in page or sidebar components.
2. Update `tt-data-league-frontend/src/config/navigation.js` to add a localized Administration navigation group using the existing sidebar icon and item structures, with child entries for Users and Roles, System Settings, and Data Import.
3. Extend `tt-data-league-frontend/src/components/sidebar/Sidebar.jsx` and related sidebar components to render the Administration group only for users with the `ADMIN` role, preserve active-state behavior for nested administration routes, and retain desktop collapsed, mobile drawer, keyboard, and close-on-selection behavior.
4. Add the required lazy-loaded administration destination/page shells in `tt-data-league-frontend/src/App.jsx` so each suboption has a stable route and protected route composition. Keep the shells deliberately focused on navigation until FEAT-00021, FEAT-00022, and FEAT-00023 provide their panel implementations.
5. Add Catalan localization keys in `tt-data-league-frontend/src/i18n/ca.js` for the Administration label, all three suboptions, route/breadcrumb labels, and any destination-shell copy. Follow the existing translation lookup conventions and avoid hard-coded user-facing text.
6. Extend focused sidebar and route tests under `tt-data-league-frontend/src/components/sidebar/` and the relevant route test files to cover administrator visibility, non-administrator hiding, parent/child active states, all three destinations, and unchanged existing navigation behavior.
7. Run the frontend validation commands from `tt-data-league-frontend`: `npm run lint` and `npm run build`. If route integration exposes a Maven-module impact, also run the repository’s prescribed Maven validation.

# Implementation Guidelines

- Keep this feature frontend-only; do not add backend endpoints or duplicate authorization logic. The UI may hide the entry for non-administrators, but every administration route must still use the existing authenticated/role guard so direct URL access is denied.
- Reuse `useAuth`, `RequireAuth`, `RequireRole`, `routePaths`, `routesMeta`, `getRouteMeta`, `isRouteActive`, `SidebarItem`, and existing responsive sidebar styling rather than introducing a second navigation or permission mechanism.
- Use the existing JavaScript/JSX conventions: ES modules, two-space indentation, single quotes, no semicolons, and existing `lucide-react` icons.
- Keep the parent Administration item accessible when expanded and make child links real keyboard-accessible links. Ensure collapsed and mobile variants expose meaningful labels and preserve the existing accessible names.
- Keep FEAT-00020 scoped to navigation and stable destination shells. Users/Roles, System Settings, and Data Import business behavior remains in FEAT-00021, FEAT-00022, and FEAT-00023 respectively.

# Acceptance Criteria

- [x] The lateral navigation panel displays a localized Administration group for administrators.
- [x] The Administration group provides Users and Roles, System Settings, and Data Import links with stable centralized routes.
- [x] Administration navigation remains hidden from non-administrator users and all destinations are protected by the existing `ADMIN` role guard.
- [x] Parent and child routes preserve nested active states and existing responsive, keyboard, mobile drawer, and close-on-selection behavior.
- [x] Each destination renders a localized navigation-only shell for the later administration panel features.
- [x] Administrators can expand and collapse the Administration suboptions from the lateral menu, with keyboard-accessible state feedback.
- [x] Administration menu labels, suboptions, breadcrumbs, and destination shells are translated in Catalan, Spanish, and English.
- [x] The expandable Administration menu option uses the same sidebar color treatment as the other menu options.

# Notes

- `FEAT-00021`, `FEAT-00022`, and `FEAT-00023` currently depend on FEAT-00020, so the three destination paths must be stable and reusable by those later panel features.
- The current sidebar filters items through route metadata and `hasPermission`, while the existing administrator-only UI uses `RequireRole role="ADMIN"`; the implementation should retain both patterns consistently.
- Implemented the administration routes, navigation group, protected destination shells, Catalan copy, and focused route/sidebar coverage. Frontend lint, build, and tests pass.
- Updated the Administration parent entry to an accessible expand/collapse control and completed administration translations for all supported locales.
- Normalized the Administration toggle button styling to match the existing sidebar options instead of the browser's default white button background.
- Shipped and validated 2026-08-30; all acceptance criteria are complete.
