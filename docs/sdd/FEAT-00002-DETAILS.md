# Build Plan

## 1. Confirm the security and API contract

1. Treat the backend as the authorization authority. Define a stable
   authenticated-user response containing the display profile plus `roles` and
   `permissions` arrays. The frontend may use these values to control
   navigation and presentation, but protected REST operations must also be
   enforced by Spring Security.
2. Standardize `GET /api/v1/auth/me` as the frontend current-user contract.
   Preserve the existing `GET /api/v1/user/me` route as a compatibility alias
   while clients migrate; do not expose password hashes or other credentials.
3. Keep the existing login and registration request shapes:
   `username`/`password` for login and `username`/`email`/`password` for
   registration. Keep the bearer-token login response compatible with
   `LoginResponse`; the frontend will call `/me` to obtain the complete
   profile and access data.
4. Define password recovery as an email-based flow:
   `POST /api/v1/auth/password/forgot` accepts an email and returns the same
   response for known and unknown accounts, while
   `POST /api/v1/auth/password/reset` accepts a one-time token and a new
   password. Recovery tokens must be single-use, expiring, stored safely, and
   never logged or returned in a normal API response.
5. Use a stateless bearer session with a token held in `sessionStorage`, not
   `localStorage`, URLs, or application logs. Clear it on logout, an invalid
   or expired-token response, and explicit session reset. The frontend must
   never treat hidden navigation as a security boundary.

## 2. Complete the backend authentication contract

1. In `tt-data-league-core-domain`, add the role/permission and password
   recovery application contracts, validation, and use cases without
   importing React or browser concerns. Add a repository port for one-time
   recovery records if persistence is required by the chosen implementation.
2. In `tt-data-league-core-repository-jpa`, persist recovery records with a
   hashed token, expiry, consumed state, and an index suitable for lookup and
   cleanup. Update the persistence schema documentation for any new table,
   columns, constraints, or relationships.
3. In `tt-data-league-api-rest`, add the `/auth/me` compatibility contract,
   recovery DTOs/controllers, and explicit validation/error responses. Map
   backend roles to Spring authorities and include the stable permission
   representation in the current-user response.
4. Replace empty authorities and the currently commented authorization rules
   in `MyUserDetailsService`, `JwtService`, and `SecurityConfig` with the
   approved role/permission matrix. Return `401` for missing or invalid
   authentication and `403` for authenticated users lacking permission.
5. Make JWT signing configuration stable across runtime restarts and
   environment-driven rather than generating a new key for every service
   instance. Keep expiry validation and blacklist-based logout explicit.
6. Configure the Vite development proxy for `/api` to the API runtime and
   keep production API calls same-origin. Do not put secrets in Vite
   configuration or committed environment files.

## 3. Add frontend session and API foundations

1. Add a small API boundary under `tt-data-league-frontend/src/api/` for
   authentication and authenticated requests. It must attach the bearer
   token, normalize known error responses, abort requests on unmount, and
   surface non-success responses instead of silently falling back.
2. Add `src/context/AuthContext.jsx` and `src/context/useAuth.js`. Keep
   `BrowserRouter` and `AppStateProvider` at the existing application root,
   then bootstrap a stored session with `/api/v1/auth/me`. Expose explicit
   `loading`, `authenticated`, and `anonymous` states, the current user,
   permission checks, login, registration, recovery, and logout actions.
3. Ensure a `401` from an authenticated request clears the session and routes
   to login with a safe internal return location. Avoid redirect loops and do
   not render protected content while the initial session is unresolved.
4. Add the Vite proxy target as environment-driven local configuration, with a
   documented default for the API runtime and no credential-bearing values.

## 4. Secure the route tree and navigation

1. Extend `src/config/routes.js` with authentication visibility and
   permission metadata, keeping paths and breadcrumb data centralized.
2. Add a reusable protected-route boundary such as
   `src/components/auth/RequireAuth.jsx` and a permission boundary/helper for
   route and action checks. Preserve the existing lazy loading, suspense
   fallback, and catch-all redirect behavior from `src/App.jsx`.
3. Add public routes for login, registration, forgot-password, and
   reset-password. Protect the existing dashboard routes, preserve the
   originally requested internal path after login, and send authenticated
   users without the required permission to a clear forbidden page.
4. Derive sidebar visibility and enabled actions from the centralized route
   metadata and `useAuth`; do not duplicate paths or authorization rules in
   individual pages. Keep the disabled Analytics item disabled until its own
   feature is available.
5. Update `UserDropdown` and the top bar to use the authenticated profile and
   real logout behavior while retaining the existing keyboard, focus, Escape,
   and responsive interactions.

## 5. Implement the authentication and recovery screens

1. Add focused auth components/pages under `src/components/auth/` and
   `src/pages/` for login, registration, password recovery request, and
   password reset. Reuse shared form controls and the existing visual tokens.
2. Provide client-side validation for required fields, email format, password
   confirmation, and server validation messages. Keep all user-facing copy in
   Catalan and expose loading, success, failure, and retry states.
3. Make registration and recovery reachable from the login flow. Do not
   reveal account existence from client messages, and never place recovery
   tokens in analytics, logs, or navigation labels.
4. Add the forbidden state with a safe link back to an allowed destination;
   it must not render protected data while explaining the access failure.

## 6. Verify behavior end to end

1. Add focused backend tests for login/register contracts, current-user
   authorization, role/permission mapping, `401`/`403` behavior, logout
   blacklisting, stable JWT configuration, and single-use recovery tokens.
2. Add focused frontend tests for provider bootstrap, token storage and
   clearing, login return navigation, route protection, permission-filtered
   navigation, logout, expired sessions, forbidden states, and recovery form
   states. Introduce only the minimum frontend test dependency needed for
   these stateful behaviors.
3. Validate the frontend with `npm ci`, `npm run lint`, and `npm run build`,
   then validate the affected Maven modules and the full reactor with
   `mvn test`.
4. Manually verify direct protected URLs, refresh with and without a token,
   expired and blacklisted tokens, login failure, registration conflicts,
   password recovery, `403` responses, keyboard navigation, mobile drawer
   behavior, and reduced-motion behavior.

# Implementation Guidelines

- Frontend checks are UX only; never rely on route guards or hidden links to
  protect data. Backend authorization must be tested independently.
- Follow the existing React 19, React Router, Context, relative-URL, Catalan
  copy, two-space JSX, single-quote, and no-semicolon conventions in
  `tt-data-league-frontend/AGENTS.md`.
- Keep authentication state in the existing Context pattern. Do not add a
  second global state library or an authentication SDK unless an explicit
  dependency decision approves it.
- Keep API calls behind focused hooks or API helpers. Every request needs
  explicit loading, success, empty, error, and cancellation behavior where
  applicable; authentication errors must not fall back to FEAT-00001 mock data.
- Preserve public backend routes while adding the target `/auth/me` contract,
  and document any persistence schema changes in
  `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`.
- Update the frontend README and runtime configuration documentation for
  proxy/API settings, token/session behavior, recovery delivery, and required
  environment variables. Never commit secrets or generated output.

# Notes

- This feature depends on the frontend shell and route structure delivered by FEAT-00001.
- Implemented: `/api/v1/auth/me` is available while `/api/v1/user/me` remains
  compatible; JWT signing is stable and environment-driven; roles and
  permissions are persisted and enforced; and recovery uses hashed,
  expiring, single-use records.
- Recovery email delivery requires the runtime `MAIL_*`,
  `PASSWORD_RECOVERY_FROM`, and `PASSWORD_RECOVERY_RESET_URL` configuration.
- The full reactor still has seven existing `tt-data-league-import` assertion
  failures where expected imported records are zero; the affected API,
  runtime, persistence, and frontend validation passes independently.
