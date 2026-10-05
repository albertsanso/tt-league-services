# Pipeline orchestrator frontend instructions

These instructions supplement the repository-level `AGENTS.md`.

## Scope

React 19 + TypeScript + Material UI single-page application (Vite) for the
pipeline control centre. It is a separate app from `tt-data-league-frontend`;
signing in and calendar components may be duplicated rather than shared.

## Boundaries

- Talks to `tt-league-pipeline-orchestrator-runtime` over relative
  `/api/pipeline/...` paths (Vite proxies them to `VITE_API_PROXY_TARGET`,
  default `http://localhost:8095`) and to the platform login over `/api/v1/...`
  (proxied to `VITE_PLATFORM_PROXY_TARGET`, default `http://localhost:8080`).
  No Java types or backend business logic here.
- The JWT lives only in the `Authorization` header and `sessionStorage`; never
  in URLs, other storage, or logs.
- UI permission checks mirror `SecurityConfiguration` and never replace server
  checks.
- DTO types live in `src/api/types.ts` and change with the runtime DTOs.
- Run list and detail state follow the event stream through the `src/runs/`
  hooks (`useRunList`, `useRunDetail`, `useRunActivity`); screens never open
  their own event connection.
- The completion category of a match day comes from the server (`completion` in the summary, computed by
  `TrackerRules.completion`); `src/calendar/completion.ts` only maps it to colours and labels and never derives it
  from the counts. Calendar and match-day detail state follow the event stream (`match-days`, `run`) through the
  `src/calendar/` hooks (`useMatchDayCalendar`, `useMatchDayDetail`, `useMatchDayResults`); screens never open their own
  event connection. Results are read from `GET .../results` and are never stored in the browser.
- Charts use `@mui/x-charts` only (MIT community edition). Statistics state goes through the `src/statistics/` hooks
  (`useStatistics`, `useReportingProgress`) and the URL filters in `statisticsFilters.ts`; screens never fetch
  statistics themselves and there is no event subscription (the page has a refresh button). Figures are never derived
  in the browser: panels show the server values (`format.ts` only formats seconds as hours) and every chart has a
  summary table that is its text equivalent.
- Whether a run can be replayed comes from the server (`replay` in the run detail, decided by `ReplayRules`);
  `src/runs/replay.ts` only maps its `code` to labels and the UI never derives eligibility from the status or the
  artifacts. A purged artifact and a reused import job are shown from `purgedAt` and `importJobReused`.
- Client-side trigger validation mirrors `TriggerRules` and
  `PipelineRun.requireValidSeason` and never replaces the server's answer.
- No data-fetching, state or JWT library without a decision.
- Tests use Vitest + React Testing Library (not Jest).
- `package-lock.json` is committed; the Maven build uses `npm ci`. If a fresh
  `npm install` fails with an `edgesOut` error, regenerate the lockfile with
  `npm install --legacy-peer-deps` and then `npm install`.
- Do not commit `node_modules/`, `dist/` or `target/`.

## Validation

```text
mvn -pl tt-league-pipeline-orchestrator-frontend -am test
```

Maven runs `npm ci`, `npm run lint` and `npm test` in the `test` phase and
`npm run build` in `prepare-package`.
