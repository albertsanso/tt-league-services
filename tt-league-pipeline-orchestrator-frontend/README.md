# tt-league-pipeline-orchestrator-frontend

Pipeline control centre: React + TypeScript + Material UI, built with Vite and
tested with Vitest and React Testing Library.

```text
npm ci
npm run dev        # dev server with the two API proxies below
npm run typecheck
npm run lint
npm test
npm run build
```

`mvn -pl tt-league-pipeline-orchestrator-frontend -am test` installs Node
v20.19.0 locally under `target/node` and runs `npm ci`, lint and tests.

## API routing

The SPA calls both backends on its own origin through two path prefixes:

| Prefix | Backend | Used for |
|---|---|---|
| `/api/pipeline` | orchestrator runtime | runs, match days, polling, `/events` stream |
| `/api/v1` | platform REST API | `/auth/login`, `/auth/me`, `/auth/logout` |

The platform has no CORS configuration, and the orchestrator's
`PIPELINE_CORS_ALLOWED_ORIGINS` is opt-in and only meant for split-origin
setups, so the same-origin prefixes avoid CORS entirely. In development Vite
proxies each prefix; in production a reverse proxy (FEAT-00116) must expose the
same prefixes and fall back to `index.html` for SPA deep links such as
`/runs/<id>`.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_PROXY_TARGET` | `http://localhost:8095` | Dev proxy target for `/api/pipeline` |
| `VITE_PLATFORM_PROXY_TARGET` | `http://localhost:8080` | Dev proxy target for `/api/v1` |
| `VITE_ORCHESTRATOR_BASE_URL` | empty (same origin) | Absolute base URL when the orchestrator is on another origin |
| `VITE_PLATFORM_BASE_URL` | empty (same origin) | Absolute base URL when the platform is on another origin |

A non-empty base URL must be an absolute `http(s)` URL; anything else fails at
startup naming the variable. See `.env.example`. The platform and the
orchestrator must share the same `JWT_SIGNING_SECRET` for the platform token to
be accepted by the orchestrator.

## Sign-in and session

- Users sign in with the platform login (`POST /api/v1/auth/login`). The JWT is
  kept in `sessionStorage` (per tab, cleared when the tab closes) and sent only in
  the `Authorization` header.
- On a page load a stored, unexpired token is validated once with
  `GET /api/v1/auth/me`, so a token revoked by a platform logout is not reused. If
  the platform cannot be reached the user is signed out with a visible message.
- A timer signs the user out when the token expires, and any orchestrator `401`
  does the same; the login page then shows "Your session has expired".
- Sign out clears the token first and then asks the platform to revoke it.
- There is no refresh token; tokens last as long as the platform configures
  (`security.jwt.expiration-millis`).

## Permissions

UI gating mirrors the orchestrator `SecurityConfiguration` and is read from the
token claims; the server still enforces every rule and screens handle `403`.

| Capability | Requirement |
|---|---|
| `trigger-runs`, `operate-match-days`, `resume-schedules` | permission `matches:write` |
| `edit-polling-policy` | role `ADMIN` (does not imply `matches:write`) |

`<Can capability mode="hide" | "disable">` hides a control or disables it with a
tooltip naming the requirement.

## Live events

`GET /api/pipeline/events` is read with `fetch` (native `EventSource` cannot send
the bearer header) by one `RunEventsProvider` per signed-in tab. Screens use
`useRunEvents(listener)` and `useEventConnection()`. After a dropped stream the
provider reconnects with a delay of `max(server retry, 5 s)` doubled per
consecutive failure up to 60 s (±20 % jitter). The server has no replay, so every
`ready` after the first emits a `reconnected` event and screens must refetch.

## Runs screen

`/runs` lists runs newest first (20 per page) with the columns Created, Source,
Trigger (`Manual · ana`, `Scheduled`, `Retry`, plus a `forced` chip), Scope (full
list in a tooltip), Duration (active rows tick every second from `startedAt` and
the local clock), Steps (one badge per Ingest / Fetch package / Import with the
latest attempt, `×n` when retried) and Outcome (status chip; failed or partial
runs show the error code, the message is in a tooltip). A row opens `/runs/:id`;
the list filters are passed along so "All runs" returns to the same view.

Filters live in the query string, so a view can be bookmarked and survives a
reload: `?source=RFETM&source=FCTT&status=FAILED&from=2026-10-01&to=2026-10-05&page=2`
(`page` is 1-based). `from` and `to` are local dates; the "to" day is **inclusive**
(the request sends the start of the next day, because the API bound is
exclusive). Unknown values are dropped and reported in a warning, never replaced
by another value; `from` after `to` sends no request. Changing a filter resets
the page.

Live updates come from the single event stream (`useRunEvents`): `run` and `step`
events patch the visible rows in place, an unknown run on the first page
triggers one debounced (300 ms) refetch, on later pages a "New runs available"
action appears, and a reconnect refetches. The "Live activity" panel is built in
the browser from the events received **since the page was opened** (there is no
replay and no log endpoint): run status changes, step starts and ends with
outcome or error, and queued-trigger changes (last 200 entries).

## Run detail

`/runs/:id` shows the header (source, season, status, trigger, requester, times,
live duration), the linked ids (`retryOfRunId`, ingest run id, import job id),
the scope, **every** step attempt, issues, artifacts (size and SHA-256), the
import report counters and a live activity log for the run. While the run is
active it follows `run` and `step` events; a terminal `run` event refetches the
detail (artifacts, report and issues only come from the GET), and a reconnect
refetches. An unknown or malformed id shows "Run not found".

## Run now dialog

Needs the `matches:write` permission (the button is disabled with a tooltip
otherwise). There are no defaults: choose the source (or All sources), type the
season (suggestions only come from the runs already loaded) and choose the scope
(Open match days, Group, Full season). Group needs exactly one source and a
filter editor (category, group, phase, territory, gender, match days as `3, 4`;
blank fields are not sent). "Ignore the ingest no-change check" sets `force`.

The client checks the same rules as the API (`PipelineRun.requireValidSeason`:
`YYYY-YYYY` with consecutive years; `TriggerRules`: group scope, filters) for
fast feedback only; the server's answer is always shown:

| Answer | Shown as |
|---|---|
| `201` / `202` | dialog closes; the page lists every source result (created with a link, queued, rejected) |
| `409` / `422` | the dialog stays open with the problem and one line per source, an "Open active run" link and the code (`NO_OPEN_MATCH_DAYS`, ...) |
| `400` with `field` | the message on that field |
| `403` / network error | an alert in the dialog; nothing is retried |

## Layout

```text
src/api/      typed HTTP client, endpoint modules, DTO types (types.ts)
src/auth/     token storage and claims, AuthProvider, permissions, Can
src/events/   SSE parser and RunEventsProvider
src/layout/   app shell, navigation list, route error boundary
src/runs/     run list/detail hooks, table, filters, activity log, Run now dialog
src/pages/    lazy-loaded screens (Calendar, Runs, Run detail, Statistics), login
```
