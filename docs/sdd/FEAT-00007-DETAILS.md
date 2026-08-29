# Build Plan

1. **Audit the existing route and navigation contract.** Review
   `tt-data-league-frontend/src/config/routes.js`,
   `src/config/navigation.js`, `src/App.jsx`, the top-bar breadcrumb, the
   sidebar, and Club search/detail/edit pages. Treat the existing route helpers
   and route metadata as the single source for paths and labels; keep the
   change limited to frontend navigation and do not add a backend contract.

2. **Define the canonical breadcrumb hierarchy.** Document and implement the
   hierarchy for the supported routes:
   `General > Cerca de clubs > Detall del club` for
   `/clubs/:clubId`, with `Cerca de clubs` linking to `/clubs`; extend the same
   chain for `/clubs/:clubId/competition/:season/:competition` and
   `/clubs/:clubId/edit`. Dynamic links must be generated through
   `routePaths`, not duplicated string literals, and must use the decoded route
   parameters safely.

3. **Correct dynamic links and state preservation.** Update breadcrumb
   generation to receive the current location, including its query string,
   so returning from competition detail or edit keeps the originating Club
   detail tab and filters where that state is relevant. Keep the Club search
   destination canonical, avoid adding query parameters that do not belong to
   the search page, and verify sidebar active-state matching for nested Club
   routes and mobile drawer selection.

4. **Harden the breadcrumb presentation.** Keep the shared `Breadcrumb`
   component responsible only for rendering route metadata. Add the
   appropriate current-page semantics (`aria-current`), preserve keyboard
   navigation and visible focus styles, keep decorative separators hidden from
   assistive technology, and ensure long labels remain usable at the existing
   desktop, tablet, and mobile breakpoints.

5. **Add focused frontend tests.** Add route metadata/helper tests and
   breadcrumb component tests covering the Club detail route entered from
   search, the Club search back link, competition and edit hierarchy, dynamic
   Club links, preserved query state, the current-page accessibility semantics,
   and nested-route sidebar active state. Extend existing page/navigation tests
   only where the corrected links change their expected destination.

6. **Validate the frontend change.** From
   `tt-data-league-frontend`, run `npm ci`, `npm test`, `npm run lint`, and
   `npm run build`. Manually check direct and refreshed Club detail, competition,
   and edit URLs, browser back/forward behavior, query-state preservation,
   desktop/mobile breadcrumb layout, keyboard focus order, screen-reader labels,
   and the mobile sidebar drawer.

# Implementation Guidelines

- Keep all route paths, labels, and breadcrumb definitions centralized in
  `src/config/routes.js`; do not hard-code route strings in page components.
- Reuse React Router location/navigation APIs, existing route helpers, and the
  current shell components. Do not introduce a second navigation state store or
  a new dependency.
- Preserve the existing authentication, permission guards, lazy loading,
  catch-all redirect, Club edit authorization, and Catalan user-facing copy.
- Treat query parameters as navigation state. Preserve them only when returning
  to a page that understands them, and do not leak Club detail filters into the
  Club search URL.
- Keep breadcrumb links as real links and current entries as non-link text with
  clear accessible semantics. Decorative icons must remain `aria-hidden`.
- Keep the scope frontend-only. No domain, persistence, REST, schema, or
  migration changes are expected.

# Notes

- Current route metadata already contains a Club detail breadcrumb, but
  `Breadcrumb` derives items from `location.pathname` only. The competition
  detail metadata currently uses `/clubs` as the Club detail link, so dynamic
  route links and return-state handling need correction together.
- `FEATURES.md` is authoritative for status and acceptance criteria. FEAT-00007
  is shipped with centralized dynamic route helpers, preserved Club detail
  query state, accessible breadcrumbs, and nested Club navigation matching.
