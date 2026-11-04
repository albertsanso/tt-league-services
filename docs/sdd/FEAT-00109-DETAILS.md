# Build Plan
All paths are relative to `tt-league-pipeline-orchestrator-frontend/`. This feature is frontend-only: no change to
the orchestrator runtime, the platform, the root POM or the module `pom.xml` (it already runs `npm ci`, `lint`,
`test` and `build`). It replaces the placeholder `src/App.tsx` shell with a signed-in shell that FEAT-00110
(runs), FEAT-00111 (calendar) and FEAT-00113 (statistics) fill with screens.

**Contracts (read from the code on 2026-10-05).**

- *Platform auth* (`tt-data-league-api-rest` `AuthController`, base `/api/v1/auth`):
  `POST /login` `{username, password}` → `200 {token, type: "Bearer", username}`; bad credentials →
  `401 {message: "Invalid username or password"}`; validation → `400`. `GET /me` (bearer) →
  `UserDto {id, username, email, createdAt, active, roles[], permissions[]}` or `401` (also for a token blacklisted
  by logout). `POST /logout` (bearer) blacklists the token in platform memory. Tokens last 30 h by default
  (`security.jwt.expiration-millis`); claims `sub`, `roles`, `permissions`, `jti`, `iat`, `exp`. There is no refresh
  endpoint and **no CORS configuration** on the platform.
- *Orchestrator API* (`tt-league-pipeline-orchestrator-runtime`, base `/api/pipeline`, bearer JWT, `ProblemDetail`
  errors with optional `code`, `field`, `errors`, `results` extensions; `401`/`403` bodies are fixed texts):
  - Runs: `POST /runs` (`TriggerRunRequest {source: RFETM|BCNESA|FCTT|ALL, season, scopeType, filters?, force}`) →
    `201` (`Location`) / `202` with `TriggerResponse {results[]}`, or `409`/`422` `ProblemDetail` whose `results`
    lists every source's `TriggerResultDto {source, outcome, code?, message?, run?, activeRunId?}`; `400` on
    validation. `GET /runs?source*&status*&from&to&page=0&size=20` (size ≤ 100, ISO instants) →
    `PageDto<RunSummaryDto>`. `GET /runs/{id}` → `RunDetailDto` (summary fields unwrapped + `steps: StepDto[]`,
    `artifacts`, `importReport`, `issues`) or `404`.
  - `GET /pending-triggers` → `PendingTriggerDto[]`.
  - Match days: `GET /match-days?source&season&state&from&to&page=0&size=50` (ISO dates) →
    `PageDto<MatchDaySummaryDto>`; `GET /match-days/{id}` → `MatchDayDetailDto {matchDay, matches, events}`;
    `POST /{id}/close`, `POST /{id}/reopen`, `PUT|DELETE /{id}/matches/{matchId}/ignore` with optional
    `{note ≤ 2000}`; `POST /{id}/notes {text, matchId?}`. All mutations return the reloaded `MatchDayDetailDto`;
    conflicts are `409` with `code` `ILLEGAL_TRANSITION` / `STALE_MATCH_DAY`, unknown id `404 MATCH_DAY_NOT_FOUND`.
  - Polling: `GET /polling/policies`, `GET /polling/policies/{source}` → `PollingPolicyDto`;
    `PUT /polling/policies/{source}` (`PollingPolicyRequest`, all fields required, ISO-8601 durations, `version`)
    → `PollingPolicyDto` or `409 STALE_POLICY`; `DELETE /polling/policies/{source}`;
    `GET /polling/schedules?source&season` → `PollScheduleDto[]`; `POST /polling/schedules/{id}/resume` →
    `PollScheduleDto`, `404`/`409` (`NOT_STOPPED`, `STALE_SCHEDULE`).
  - Events: `GET /events` (`text/event-stream`) sends `retry: 5000` and `event: ready` first, then `run`
    (`RunSummaryDto` without `steps`), `step` (`StepDto`), `pending-trigger`
    (`{source, state: QUEUED|LAUNCHED|DROPPED, requestedBy, runId?, code?}`) and `: keep-alive` comments; no replay;
    `503` beyond the subscriber cap; the emitter times out after 30 min by default.
  - Authorities (`SecurityConfiguration`): any valid token can read; `matches:write` (token `permissions`) for
    `POST /runs`, every match-day mutation and schedule resume; role `ADMIN` (token `roles`) for policy
    `PUT`/`DELETE`. `ADMIN` does not imply `matches:write`.

**Build steps.**

1. **Routing contract and configuration (`vite.config.ts`, `src/config.ts`, `src/vite-env.d.ts`, `.env.example`).**
   - The SPA calls both backends on its own origin through two path prefixes: `/api/pipeline` → orchestrator,
     `/api/v1` → platform. Dev: two Vite proxy rules, `VITE_API_PROXY_TARGET` (default `http://localhost:8095`,
     unchanged name) and new `VITE_PLATFORM_PROXY_TARGET` (default `http://localhost:8080`). The current catch-all
     `/api` rule is replaced by the `/api/pipeline` rule. Production: the same prefixes on the FEAT-00116 reverse
     proxy. No CORS change anywhere.
   - `src/config.ts` exports `apiConfig = {orchestratorBaseUrl, platformBaseUrl}` from
     `VITE_ORCHESTRATOR_BASE_URL` / `VITE_PLATFORM_BASE_URL`, both default `''` (same origin). A non-empty value
     must be an absolute `http(s)` URL; it is normalised without a trailing slash; anything else throws at startup
     with the variable name in the message. Typed in `vite-env.d.ts`.
   - `.env.example` lists the four variables with one-line comments. Test: `config.test.ts` (defaults, valid URL,
     trailing slash, invalid value throws).

2. **API types (`src/api/types.ts`).** Hand-written `readonly` TypeScript mirrors of every DTO above:
   `RunSummary`, `StepStatus`, `RunDetail` (summary intersection + `steps: Step[]`), `Step`, `ScopeFilter`
   (`category, group, phase, territory, gender, matchDays`), `RunError`, `Artifact`, `ImportReport` (ten counters),
   `Page<T>`, `TriggerRunRequest`, `TriggerResult`, `TriggerResponse`, `PendingTrigger`, `MatchDaySummary`
   (including `matchCounts: Record<TrackedMatchStatus, number>`), `TrackedMatch`, `MatchDayEvent`,
   `MatchDayDetail`, `MatchDayActionRequest`, `MatchDayNoteRequest`, `PollingPolicy`, `PollingPolicyRequest`,
   `PollSchedule`, `Problem` (`type, title, status, detail, instance, code?, field?, errors?, results?`), and the
   event payloads `RunEvent`, `StepEvent`, `PendingTriggerEvent`. Closed sets are string-literal unions matching the
   Java enums (`PipelineSource`, `RunStatus`, `RunTrigger`, `StepKind`, `StepStatus`, `ScopeType`, `TriggerOutcome`,
   `MatchDayState`, `TrackedMatchStatus`, `PolicyLevel`); read each enum in the core module while writing them.
   Instants, dates and durations stay ISO strings; UUIDs are `string`. Use `import type` (the project sets
   `verbatimModuleSyntax`).

3. **HTTP client (`src/api/client.ts`, `src/api/ApiError.ts`).** `createHttpClient({baseUrl, getToken,
   onUnauthorized, fetch?})` returning `request<T>(method, path, {query?, body?, signal?})`:
   - Adds `Accept: application/json`, `Authorization: Bearer <token>` when a token exists, and
     `Content-Type: application/json` with a JSON body; serialises arrays as repeated params and omits
     `undefined`/`null`/empty values; returns `undefined` for `204` or an empty body.
   - Non-2xx throws `ApiError {status, problem, message}`: `problem` is the parsed body when it is JSON
     (`application/problem+json` or `application/json`), else `null`; `message` is `detail`, then platform
     `message`, then `title`, then `Request failed (<status>)`. A `fetch` rejection becomes `ApiError` with status
     `0` and "Cannot reach the server", except `AbortError`, which is rethrown unchanged.
   - `401` calls `onUnauthorized()` before throwing. No retries, no token in URLs, nothing logged.
   - Tests (`client.test.ts`, fake `fetch`): headers, repeated params and omitted values, JSON body, `204`, problem
     mapping with `code`/`field`/`results`, plain-JSON platform error, non-JSON error, network error, abort, `401`
     callback.

4. **Endpoint modules (`src/api/auth.ts`, `runs.ts`, `pendingTriggers.ts`, `matchDays.ts`, `polling.ts`).** Plain
   functions that take the client, one per endpoint in Contracts, with explicit parameter and return types:
   `login`, `currentUser`, `logout`; `listRuns(RunListQuery)`, `getRun`, `triggerRun`; `listPendingTriggers`;
   `listMatchDays(MatchDayListQuery)`, `getMatchDay`, `closeMatchDay`, `reopenMatchDay`, `ignoreMatch`,
   `unignoreMatch`, `addMatchDayNote`; `listPollingPolicies`, `getPollingPolicy`, `replacePollingPolicy`,
   `deletePollingPolicy`, `listPollSchedules`, `resumePollSchedule`. `triggerRun` returns
   `{status: 201 | 202, response}`; for `409`/`422` callers read `error.problem.results` (helper
   `triggerResults(error)`). `auth.ts` uses a second client on the platform base without `onUnauthorized` (a `401`
   at login is a normal outcome). Path segments are `encodeURIComponent`-ed.
   - Test (`endpoints.test.ts`): one table-driven case per function asserting method, path, query and body, plus
     the `triggerRun` `201`/`202`/`409` handling.

5. **Token and claims (`src/auth/tokenStorage.ts`, `src/auth/claims.ts`).**
   - Token kept in `sessionStorage` under `tt-league.pipeline.auth-token` (per tab, gone when the tab closes; the
     same choice as `tt-data-league-frontend`). Every storage call is guarded so a blocked storage degrades to
     memory only.
   - `decodeClaims(token)` base64url-decodes the payload (UTF-8 safe) into `{subject, roles, permissions,
     expiresAt}`; returns `null` when the token is malformed or has no numeric `exp`; non-array `roles` /
     `permissions` become `[]`. No signature check (the backends validate); claims drive display, permission gating
     and the expiry timer only. Permissions are read from the token, not from `/me`, because the orchestrator
     enforces the token's claims.
   - Tests: valid token, padding variants, non-ASCII subject, malformed parts, missing `exp`, non-array claims,
     storage throwing.

6. **Auth provider (`src/auth/AuthProvider.tsx`, `src/auth/authContext.ts`, `src/auth/useAuth.ts`).** Context value
   `{status: 'restoring' | 'signed-out' | 'signed-in', user?: {username, roles, permissions, expiresAt}, token?,
   signOutReason?, signIn(username, password), signOut(reason?: 'expired' | 'user')}`. Context object, hook and
   provider live in separate files (react-refresh lint rule).
   - Restore on start: a stored token whose `exp` is in the future is validated once with `GET /api/v1/auth/me`
     (catches tokens revoked by a platform logout); `401` or an expired/undecodable token clears storage and goes
     to `signed-out`; a network error also goes to `signed-out` with a visible message (no silent sign-in).
   - `signIn`: `POST /login`; on `200` the token must decode and not be expired, otherwise fail with "The sign-in
     response was invalid"; store and switch to `signed-in`. `401` shows the platform message; other errors show
     `ApiError.message`.
   - Expiry: one `setTimeout` to `expiresAt` (capped at 2^31-1 ms and re-armed) calls `signOut('expired')`. Any
     orchestrator `401` (from the client's `onUnauthorized` or the event stream) does the same.
   - `signOut`: clears storage and state first, then fires `POST /logout` for `'user'` sign-outs without awaiting it
     (an expired token is not sent). Navigation is done by the route guard, not by the provider.
   - Tests (`AuthProvider.test.tsx`, fake `fetch`, fake timers): sign-in success and stored token; wrong password
     message; invalid login response; restore with valid token calls `/me`; restore with revoked token (`/me` 401);
     expired stored token never calls `/me`; timer expiry signs out with reason `expired`; orchestrator 401 signs
     out; user sign-out calls `/logout` and clears storage.

7. **Permissions (`src/auth/permissions.ts`, `src/auth/Can.tsx`).** `type Capability = 'trigger-runs' |
   'operate-match-days' | 'resume-schedules' | 'edit-polling-policy'` mapped to `matches:write` (first three) and
   role `ADMIN` (last), in one table that mirrors `SecurityConfiguration`. `useCan(capability): boolean` and
   `<Can capability mode="hide" | "disable">`: `hide` renders nothing; `disable` clones the single child with
   `disabled` and wraps it in a `Tooltip` "Requires the matches:write permission" / "Requires the ADMIN role" (a
   `span` wrapper so the tooltip works on a disabled button). Screens must still handle `403` from the API.
   - Tests (`Can.test.tsx`): each capability with tokens carrying `matches:write` only, `ADMIN` only, both, neither;
     both modes; `ADMIN` alone cannot trigger runs.

8. **API context (`src/api/ApiProvider.tsx`, `src/api/apiContext.ts`, `src/api/useApi.ts`).** Builds the
   orchestrator client from `apiConfig` and `AuthProvider` (`getToken` reads the current token through a ref so the
   client is created once; `onUnauthorized` → `signOut('expired')`) and exposes the bound endpoint modules:
   `useApi().runs.listRuns(...)`, etc. Tests render consumers with `ApiProvider` over a fake `fetch`, or with a
   `TestApiProvider` (`src/test/`) that injects stubs.

9. **Server-Sent Events (`src/events/parseSse.ts`, `src/events/RunEventsProvider.tsx`, `src/events/useRunEvents.ts`).**
   Native `EventSource` cannot send `Authorization`, so the stream is read with `fetch`.
   - `parseSse`: an incremental parser (feed text chunks, get `{event, data, id?}` records and `retry` values)
     following the SSE spec: `\n`/`\r\n`/`\r` line ends, multi-line `data`, default event `message`, comment lines,
     records split across chunks, BOM. Decoding uses `TextDecoder` with `{stream: true}`.
   - `RunEventsProvider`, mounted inside the signed-in shell, owns **one** connection per signed-in session:
     `GET {orchestratorBaseUrl}/api/pipeline/events` with the bearer header and an `AbortController`. It exposes
     `connection: {state: 'connecting' | 'open' | 'reconnecting' | 'stopped', lastEventAt?}` and
     `subscribe(listener): unsubscribe`. Listeners receive typed `{type: 'run' | 'step' | 'pending-trigger',
     payload}` and `{type: 'reconnected'}`; `ready` moves to `open`; unknown event names are ignored; an
     unparsable payload is dropped with a `console.warn` naming only the event type.
   - Reconnection: after the stream ends or fails, wait `max(server retry, 5 s)` doubled per consecutive failure up
     to 60 s, with ±20 % jitter, then reconnect; the delay resets after a `ready`. Every `ready` after the first one
     emits `reconnected` so screens refetch (the server has no replay). `401` → `stopped` and `signOut('expired')`;
     `503` and network errors → `reconnecting`. A token change restarts the connection; sign-out or unmount aborts
     it and clears timers. Listener exceptions are caught per listener and logged, so one screen cannot break the
     stream for others.
   - `useRunEvents(listener)` subscribes for the component's lifetime (latest listener through a ref);
     `useEventConnection()` returns the connection state.
   - Tests (`parseSse.test.ts`; `RunEventsProvider.test.tsx` with a fake `fetch` returning a controllable
     `ReadableStream` and fake timers): parser cases above; bearer header and `Accept: text/event-stream` sent;
     `ready` → `open`; `run`/`step`/`pending-trigger` delivered typed; keep-alive ignored but updates `lastEventAt`;
     stream end → reconnect after backoff, growth and reset; `reconnected` only from the second `ready`; `401` stops
     and signs out; `503` retries; malformed payload dropped; failing listener isolated; unmount aborts and no
     reconnect afterwards.

10. **Shell layout (`src/layout/AppLayout.tsx`, `src/layout/navigation.ts`, `src/theme.ts`).**
    - `navigation.ts` is the single list of sections: Calendar `/calendar`, Runs `/runs`, Statistics `/statistics`
      (label, path, icon). Later features change only their page module.
    - `AppLayout`: MUI `AppBar` (title "Pipeline control centre", event-stream indicator with a text label and
      tooltip, username, Sign out button), a permanent `Drawer` from `md` up and a temporary one toggled by a menu
      button below `md`; nav entries are `NavLink`s with `aria-current="page"` on the active section. The routed
      `<Outlet />` sits inside `Suspense` (centred `CircularProgress`) and a `RouteErrorBoundary` that shows an
      error `Alert` with a Reload button (covers failed lazy-chunk loads).
    - `theme.ts` keeps the existing primary colour; add only the drawer width constant.
    - Dependency: `@mui/icons-material` (same major as `@mui/material`) for the navigation and app-bar icons. No
      other new runtime dependency.

11. **Routes and pages (`src/App.tsx`, `src/auth/RequireAuth.tsx`, `src/pages/*`).**
    - `/login` → `LoginPage` (public); a pathless `RequireAuth` route (shows a full-page spinner while `restoring`,
      redirects to `/login` with `state.from` when signed out) wrapping `RunEventsProvider` + `AppLayout` with
      `index` → `<Navigate to="/runs" replace />`, `calendar`, `runs`, `runs/:runId`, `statistics`; `*` →
      `NotFoundPage` inside the layout.
    - `CalendarPage`, `RunsPage`, `RunDetailPage` and `StatisticsPage` are `React.lazy` default exports in their
      own modules (separate chunks). In this feature they are minimal: a heading and an empty-state message. To prove
      the client, the event stream and permission gating end to end, `RunsPage` shows the latest 20 runs (`listRuns`,
      source / status / created / requested-by columns, refetched on `run` and `reconnected` events) and a
      **Run now** button wrapped in `<Can capability="trigger-runs" mode="disable">` that does nothing yet. FEAT-00110
      replaces this page and owns the dialog.
    - `LoginPage`: centred MUI card with username and password (`autoComplete` set), submit disabled while pending,
      error `Alert`, "Your session has expired. Sign in again." when `signOutReason === 'expired'`; after sign-in it
      navigates to `state.from` (only same-app paths) or `/runs`. A signed-in visitor is redirected away.
    - `main.tsx`: `ThemeProvider` → `CssBaseline` → `BrowserRouter` → `AuthProvider` → `ApiProvider` → `App`.
    - Tests (`App.test.tsx` replaces the current one; `LoginPage.test.tsx`; `RunsPage.test.tsx`), with
      `MemoryRouter` and a fake `fetch`: signed-out deep link → login → back to the deep link; `/` → `/runs`; the
      three nav entries render and the active one is marked; each lazy route renders (`findBy*`); unknown path →
      not found; sign out → login; expiry while on a page → login with the expired banner; `RunsPage` lists runs,
      refetches on a `run` event, and disables Run now without `matches:write`.

12. **Documentation.**
    - `README.md`: the two-prefix routing contract and why (platform has no CORS; the orchestrator's
      `PIPELINE_CORS_ALLOWED_ORIGINS` is only for split-origin setups); all environment variables; sign-in, token
      storage, expiry and revocation behaviour; permission gating table; event-stream behaviour (fetch streaming,
      backoff, refetch on reconnect, one connection per tab); SPA deep links need an `index.html` fallback on the
      serving proxy (FEAT-00116); project layout (`api/`, `auth/`, `events/`, `layout/`, `pages/`).
    - `AGENTS.md` (module): Boundaries updated with the two prefixes, "token only in the `Authorization` header,
      never in URLs, storage other than `sessionStorage`, or logs", "UI permission checks mirror
      `SecurityConfiguration` and never replace server checks", "DTO types live in `src/api/types.ts` and change with
      the runtime DTOs", "no data-fetching, state or JWT library without a decision".

13. **Validation.** In the module: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` (check that each
    page is a separate chunk). Then `mvn -pl tt-league-pipeline-orchestrator-frontend -am test` and the full
    `mvn test`. Manual smoke: platform on 8080 and orchestrator on 8095 with the same `JWT_SIGNING_SECRET`,
    `npm run dev`, sign in, open Runs, trigger a run with curl and see the table refresh, stop the orchestrator and
    see the indicator go to reconnecting and recover. Review the diff for `dist/`, `node_modules/`, `target/` or `.env`
    files.

## Acceptance Criteria

- [ ] Users sign in through the platform login endpoint; the JWT is kept for the session and expiry sends the user back to sign in
- [ ] The layout has navigation for Calendar, Runs and Statistics, with routes lazy-loaded
- [ ] A typed API client covers the orchestrator endpoints, and a hook subscribes to the SSE event stream with reconnection
- [ ] Controls that need `matches:write` or `ADMIN` are hidden or disabled for other users
- [ ] Vitest + React Testing Library tests cover login, routing, the client and the SSE hook

# Implementation Guidelines

- Keep backend rules in the backend; the UI only reflects permissions it receives and still handles `401`/`403`.
- Follow the root and module `AGENTS.md`: Vitest + React Testing Library only; `npm ci` with the committed lockfile
  (regenerate it as the module `AGENTS.md` describes if `npm install` fails); no `node_modules/`, `dist/`, `target/`
  or `.env` files in the diff.
- No data-fetching, state-management or JWT library: `fetch`, React context and a small claims decoder are enough.
  The only new runtime dependency is `@mui/icons-material`.
- The JWT travels only in the `Authorization` header. It is never logged, put in a URL or query string, written to
  `localStorage`, or echoed in an error message. Claims are decoded for display, gating and expiry only.
- Permission gating has one table mirroring `SecurityConfiguration`: `matches:write` for triggering runs, match-day
  mutations and resuming schedules; role `ADMIN` for polling-policy changes. `ADMIN` alone does not grant
  `matches:write`.
- No hard-coded hosts: base URLs and proxy targets come from `VITE_*` variables; an invalid value fails at startup.
  There is no default season, source or scope anywhere in the client.
- Explicit failure: no silent sign-in on restore errors, no swallowed API errors, no success-shaped fallbacks. The
  only broad catches are the per-listener isolation in the event provider and the route error boundary.
- TypeScript strict mode, `import type` for types, and context objects, hooks and providers in separate files (lint
  rule `react-refresh/only-export-components`).
- Duplicating login code from `tt-data-league-frontend` is acceptable (D10); do not create a shared package.
- Out of scope: the real Runs, run-detail, Run now dialog, Calendar, match-day detail and Statistics screens
  (FEAT-00110, FEAT-00111, FEAT-00113); a generic data-loading hook (added by the first screen that needs one);
  reverse-proxy and SPA-fallback configuration (FEAT-00116); refresh tokens; CORS changes on either backend;
  any backend change.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Open question in the baseline item: separate frontend vs a Pipeline area in `tt-data-league-frontend`.

2026-10-04: resolved as a separate application (D10). Platform login needs CORS for the orchestrator frontend
origin, or both apps served behind one reverse proxy in the Compose setup (FEAT-00116).

2026-10-05: plan built (status `idea` -> `planned`). Decisions made while planning, for the user to confirm before `ready`:
- **Two same-origin prefixes.** The platform has no CORS configuration and `FEAT-00105` made CORS on the orchestrator
  opt-in, so the SPA calls `/api/v1/auth/*` (platform) and `/api/pipeline/*` (orchestrator) on its own origin. Dev:
  two Vite proxy rules; Compose: reverse proxy with the same prefixes (FEAT-00116). This replaces the module's
  single `/api` proxy rule.
- **Session storage for the token** ("kept for the session"): per-tab, cleared on close; an expiry timer and any `401`
  return the user to `/login`. No refresh token exists on the platform.
- **Typed client covers every orchestrator endpoint that exists today**, including match-day and polling endpoints from
  FEAT-00107/FEAT-00108, so FEAT-00110/00111/00113 only add screens. Types are hand-written; the plan lists DTOs that
  must be re-read while implementing.
- **Single shared SSE connection** per session through a provider; reconnect uses backoff and the consumer refetches
  because the server has no replay.
- Screens stay placeholders; `RunsPage` shows a plain first-page table and a gated Run now button placeholder only to
  prove the client and permission wiring.

Open questions: none blocking. Confirm that adding `@mui/icons-material` is acceptable (otherwise use text-only
navigation).

2026-10-05: plan rebuilt from scratch on request (status stays `planned`). Every contract was re-read from the code,
including the DTOs and request records the first plan left to the implementer. Changes from the first plan:
- Contracts now list exact request/response shapes, status codes and error codes for platform auth, runs, pending
  triggers, match days, polling and the event stream, so the types step needs no guessing.
- Restoring a stored token validates it once with platform `GET /api/v1/auth/me`, so a token revoked by a platform
  logout is not reused. Gating still reads the token's claims, because those are what the orchestrator enforces.
- Permissions are expressed as capabilities (`trigger-runs`, `operate-match-days`, `resume-schedules`,
  `edit-polling-policy`) in one table, instead of a raw `trigger`/`admin` flag.
- The event provider gains jitter, a 60 s cap, a `reconnected` signal from the second `ready` onwards, per-listener
  isolation, and a restart when the token changes.
- Route error boundary for failed lazy-chunk loads; restore spinner; safe `state.from` redirect after sign-in.
- Base-URL variables renamed to `VITE_ORCHESTRATOR_BASE_URL` / `VITE_PLATFORM_BASE_URL`; `VITE_API_PROXY_TARGET`
  keeps its name.
- The plan-replacement helper drops the details `## Acceptance Criteria` subsection; it was restored by hand
  (criteria unchanged).

Open questions before `ready`: confirm `@mui/icons-material`, and confirm the `/me` check on restore (one extra
platform call per page load).

2026-10-05: user confirmed `@mui/icons-material` and the `/me` check on restore; plan approved, status `planned` ->
`ready`.

2026-10-05: implemented in `tt-league-pipeline-orchestrator-frontend` (frontend only). Validation: `npm run typecheck`, `npm run lint`, `npm test` (135 tests), `npm run build` (each page is its own chunk) and `mvn -pl tt-league-pipeline-orchestrator-frontend -am test` all pass. Deviations from the plan:
- `AuthContextValue` also exposes a stable `getToken()` and an optional `notice` (restore-failure message), so `ApiProvider` and the restore flow avoid reading refs during render (react-hooks lint rules).
- `reconnectDelay` and `safeRedirectPath` live in their own files (react-refresh lint rule); the starting session is derived from storage in a `useState` initializer, and only the `/me` validation runs in an effect.
- Test-only helpers added under `src/test/` (`fakeFetch`, `jwt`, `renderApp`, `TestApiProvider`); `setup.ts` raises the RTL async timeout to 5 s because lazy route chunks are slow to transform under parallel load.
- Not done: the manual smoke test against live platform and orchestrator (needs both services running).
- Full `mvn test` fails in `tt-data-league-import` (`BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas`), a module this change does not touch; the orchestrator frontend module passes on its own.
