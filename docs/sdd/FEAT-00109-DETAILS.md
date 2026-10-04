# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-frontend`: MUI theme and layout, React Router, auth context, typed client (`fetch`), SSE hook.
2. Configuration of API base URLs through Vite environment variables (no hard-coded hosts).
3. Tests; module README.

## Acceptance Criteria

- [ ] Users sign in through the platform login endpoint; the JWT is kept for the session and expiry sends the user back to sign in
- [ ] The layout has navigation for Calendar, Runs and Statistics, with routes lazy-loaded
- [ ] A typed API client covers the orchestrator endpoints, and a hook subscribes to the SSE event stream with reconnection
- [ ] Controls that need `matches:write` or `ADMIN` are hidden or disabled for other users
- [ ] Vitest + React Testing Library tests cover login, routing, the client and the SSE hook

# Implementation Guidelines

- Keep backend rules in the backend; the UI only reflects permissions it receives.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Open question in the baseline item: separate frontend vs a Pipeline area in `tt-data-league-frontend`.

2026-10-04: resolved as a separate application (D10). Platform login needs CORS for the orchestrator frontend
origin, or both apps served behind one reverse proxy in the Compose setup (FEAT-00116).
