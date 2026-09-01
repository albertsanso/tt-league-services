# Frontend module instructions

## Scope

This module is the TT League single-page application. It is a React 19
application built with Vite and JavaScript/JSX. The current frontend delivers
the FEAT-00001 dashboard shell, overview, search placeholders, and settings
page; treat the existing UI as an evolving product surface rather than as a
second implementation of backend business rules.

These instructions supplement the repository-level `AGENTS.md`. Read both
files before changing this module.

## Architecture and boundaries

- `src/main.jsx` is the application entry point. Keep `BrowserRouter` and
  `AppStateProvider` at the application root unless a change explicitly
  requires a different provider boundary.
- `src/App.jsx` owns route composition. Page routes are lazy-loaded and are
  rendered inside `DashboardLayout`; preserve the suspense fallback and the
  catch-all redirect when adding routes.
- Put route metadata and navigation labels in `src/config/routes.js` and
  `src/config/navigation.js`. Do not duplicate paths or breadcrumb metadata in
  individual pages.
- Keep shared shell code in `src/layouts`, `src/components/sidebar`, and
  `src/components/topbar`. Put reusable visual primitives in
  `src/components/ui`; put page-specific composition in `src/pages` or the
  relevant feature component directory.
- Keep cross-cutting browser UI state in `src/context` and expose it through
  hooks such as `useAppState`. Do not introduce a second global state mechanism
  for state that belongs in the existing provider.
- Keep reusable asynchronous behavior in `src/hooks`. Effects that register
  listeners, timers, or requests must clean them up on unmount.
- The frontend communicates with backend services through HTTP APIs. Use
  relative API paths such as `/api/stats/community`; do not import Java classes
  or persistence types into this module and do not duplicate backend domain
  logic in components.

## API, configuration, and data handling

- All `VITE_*` values are embedded in the browser bundle and are public
  configuration, not secrets. Never put credentials, tokens, or passwords in
  source code, `.env` files committed to the repository, or client-side
  configuration.
- Preserve the current community-stats behavior: mock data is deterministic
  by default and real data is selected only when
  `VITE_USE_MOCK_STATS=false`. Keep the request abort behavior and visible
  error state when modifying `useCommunityStats`.
- Normalize external API data at the hook or API boundary before rendering it.
  Components should receive stable, display-oriented values rather than
  handling multiple backend response shapes.
- Do not add broad catches or silent success fallbacks. If an API failure can
  be represented with the existing mock behavior, keep that behavior explicit;
  otherwise expose loading, empty, and error states to the user.
- Coordinate API contract changes with the REST or GraphQL modules. A
  frontend-only change must not assume an endpoint, response field, or
  authentication behavior that is not implemented by the backend.

## UI and code conventions

- Use ES modules, JavaScript/JSX, two-space indentation, single quotes, and no
  semicolons, matching the existing source and ESLint configuration.
- Follow `docs/frontend/design-contract.md` for the compact, enforceable version
  of the visual system. Treat `docs/frontend/theme-spec.md` as the complete
  visual reference when a design decision is not covered by the contract.
- Prefer small function components and focused hooks. Keep derived values
  derived rather than duplicating them in state, and use functional state
  updates when the next value depends on the previous value.
- Prefer existing components, hooks, route helpers, context values, UI
  primitives, and `lucide-react` icons before introducing new abstractions or
  dependencies. New shared visual behavior belongs in `src/components/ui`.
- Keep user-facing copy consistent with the existing Catalan interface.
  Preserve established loading, empty, error, and responsive states when
  extending a page.
- New interactive controls must be keyboard accessible, have an appropriate
  accessible name, and expose loading or error status where applicable. Do not
  use clickable non-interactive elements when a button or link is appropriate.
- Keep styling in the existing CSS layers (`src/index.css` and `src/app.css`)
  unless component-specific styling has a clear, consistent home. Preserve
  the mobile, tablet, and desktop behavior managed by `AppStateProvider`.
- Do not add a dependency solely to solve a problem already covered by React,
  React Router, the existing hooks, or the current CSS setup. When a new
  dependency is necessary, update `package-lock.json` with npm rather than
  editing the lockfile manually.

## Change workflow

1. Inspect the relevant page, route metadata, navigation entry, shared shell
   components, and hooks before editing.
2. For a new page, add its route, route metadata, navigation entry, lazy import,
   loading state, and responsive shell integration as one coherent change.
3. For API-backed behavior, verify the endpoint contract and handle loading,
   success, empty, failure, and cancellation paths.
4. Apply the design contract and reuse UI primitives before adding page-specific
   styles.
5. Keep changes focused. Do not edit Maven modules, generated output, or
   unrelated global styles to make a frontend change appear to work.
6. Review the final diff for accidental changes, exposed client configuration,
   broken links, missing accessible names, and untranslated user-facing copy.

## Validation

Use the existing commands from this module:

```text
npm ci
npm run lint
npm run build
```

For local development:

```text
npm run dev
```

The development server is normally available at
`http://localhost:5173`. When a change affects Maven integration or the full
reactor, also run:

```text
mvn -pl tt-data-league-frontend -am test
mvn test
```

Do not add a test framework or new build tool unless the task requires it.
The current FEAT-00001 module does not define component tests; when adding
substantial stateful or API behavior, introduce focused tests only as part of
that feature's agreed implementation.

## Generated files and operational safety

- Do not commit `node_modules`, `dist`, Maven `target` output, logs, local
  environment files, or generated bundles.
- Keep Node and npm versions aligned with `pom.xml`:
  Node `20.19.0` and npm `10.2.4` are installed by
  `frontend-maven-plugin`.
- Run npm commands from `tt-data-league-frontend`; run Maven commands from the
  repository root.
- Do not use destructive Git commands or rewrite history. Preserve unrelated
  worktree changes.
