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

## Layout

```text
src/api/      typed HTTP client, endpoint modules, DTO types (types.ts)
src/auth/     token storage and claims, AuthProvider, permissions, Can
src/events/   SSE parser and RunEventsProvider
src/layout/   app shell, navigation list, route error boundary
src/pages/    lazy-loaded screens (Calendar, Runs, Run detail, Statistics), login
```
