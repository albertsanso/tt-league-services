# Pipeline orchestrator frontend instructions

These instructions supplement the repository-level `AGENTS.md`.

## Scope

React 19 + TypeScript + Material UI single-page application (Vite) for the
pipeline control centre. It is a separate app from `tt-data-league-frontend`;
signing in and calendar components may be duplicated rather than shared.

## Boundaries

- Talks to `tt-league-pipeline-orchestrator-runtime` over relative `/api/...`
  paths (Vite proxies `/api` to `VITE_API_PROXY_TARGET`, default
  `http://localhost:8095`). No Java types or backend business logic here.
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
