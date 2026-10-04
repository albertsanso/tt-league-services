# tt-league-pipeline-orchestrator-frontend

Pipeline control centre: React + TypeScript + Material UI, built with Vite and
tested with Vitest and React Testing Library.

```text
npm ci
npm run dev        # dev server, proxies /api to VITE_API_PROXY_TARGET (default http://localhost:8095)
npm run typecheck
npm run lint
npm test
npm run build
```

`mvn -pl tt-league-pipeline-orchestrator-frontend -am test` installs Node
v20.19.0 locally under `target/node` and runs `npm ci`, lint and tests.
