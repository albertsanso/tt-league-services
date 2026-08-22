# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template below.
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `done` | Shipped |
| `blocked` | Waiting on a dependency or decision |

---

## Template

Copy this block to add a new feature:

```
### [FEAT-000] Feature Name
- **Status:** idea
- **Priority:** low | medium | high
- **Effort:** small (< 2h) | medium (2–8h) | large (> 8h)
- **Depends on:** —

#### Goal
One sentence: what problem does this solve for the user?

#### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

#### Feature Details
→ See [FEAT-000-DETAILS.md](./FEAT-000-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.
```

### Feature Details file format
```
# Build Plan
> Fill this in when status moves to `planned`.

1. Step 1
2. Step 2
...

# Implementation Guidelines

# Notes
Any open questions, design decisions, or links.
```
## In Progress

## Backlog

## Done

### [FEAT-002] Access control and secured navigation
- **Status:** done
- **Priority:** high
- **Effort:** large (> 8h)
- **Depends on:** FEAT-001

#### Goal
Protect application routes and data while presenting users only with navigation options permitted by their access level.

#### REST API integration
The frontend integrates with backend authentication and authorization endpoints to manage user sessions and permissions. The backend remains the authority for every protected operation.

**Strategy:** Route guards, context providers, and state management control navigation and UI presentation while Spring Security enforces protected operations.
**Architecture:** same-origin relative URLs + Vite dev proxy

#### REST API endpoints
- `POST /api/v1/auth/login` — Authenticate user and return a JWT token.
- `POST /api/v1/auth/register` — Register a new user account.
- `POST /api/v1/auth/logout` — Invalidate the current user session.
- `GET /api/v1/auth/me` — Return the authenticated user's profile and permissions; `/api/v1/user/me` remains a compatibility alias.
- `POST /api/v1/auth/password/forgot` — Start an email-based password recovery flow without revealing whether an account exists.
- `POST /api/v1/auth/password/reset` — Consume a one-time, expiring recovery token and set a new password.

#### Acceptance Criteria
- [x] Unauthenticated users are redirected to a login flow when accessing protected routes.
- [x] Authenticated users can access only the routes and actions allowed by their permissions.
- [x] Navigation reflects the current user's permissions and does not expose unauthorized destinations.
- [x] Unauthorized access produces a clear forbidden state without disclosing protected data.
- [x] Login page can allow a user registration flow, password reset, and account recovery.

#### Feature Details
→ See [FEAT-002-DETAILS.md](./FEAT-002-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-001] Frontend application skeleton and theme
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Create a basic frontend application skeleton with a consistent theme, including routing, state management, and UI components.
Expand `docs/frontend/theme-spec.md` into an implementation sub-plan inside
`FEAT-001-DETAILS.md`, then use that sub-plan to implement the application
shell, overview page, responsive behavior, accessibility, and visual system.

#### Acceptance Criteria
- [x] A frontend application skeleton is created with routing, state management, and UI components.
- [x] `FEAT-001-DETAILS.md` contains an explicit sub-plan covering the sections and implementation requirements of `docs/frontend/theme-spec.md`.
- [x] The application follows the theme specifications defined in `docs/frontend/theme-spec.md` and the expanded sub-plan.
- [x] The application is responsive and works on different screen sizes, desktop and mobile.

#### Feature Details
→ See [FEAT-001-DETAILS.md](./FEAT-001-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---