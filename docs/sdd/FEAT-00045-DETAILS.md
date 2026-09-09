# Build Plan
1. **Add `InitialUserProvisioningService`** in
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/auth/user/service/`,
   `@Named`, constructor-injected (`@Inject`) with `UserRepository` and `PasswordEncoder`
   only (do not depend on `UserAdminService` or `UserValidator` — see Implementation
   Guidelines for why password validation is intentionally bypassed here).
   - Define the fixed seed list as a private `List<SeedUser>` (a small local record/holder
     with `username`, `email`, `plainPassword`, `role`) matching the Goal exactly:
     - `albert` / `albert@localhost` / `albert` / `ADMIN`
     - `oscar` / `oscar@localhost` / `Oscar&1234` / `ADMIN`
   - `ensureDefaultUsersExist()`: iterate the seed list; for each entry, skip (log at
     `info`, no-op) if `userRepository.existsByUsername(username)` or
     `existsByEmail(email)` is true; otherwise build
     `User.createNew(username, email, passwordEncoder.encode(plainPassword))`, call
     `setRoles(Set.of(role))`, and `userRepository.save(user)`. Check/create each user
     independently so a partially-seeded system (e.g. `albert` already exists but `oscar`
     doesn't) still converges to both users present, and re-running after both exist is a
     full no-op.

2. **Add `InitialUserStartupInitializer`** (`CommandLineRunner`) in
   `tt-data-league-api-runtime/src/main/java/org/cttelsamicsterrassa/data/api/runtime/config/`,
   modeled on `ImportFolderSettingStartupInitializer`: injects
   `InitialUserProvisioningService`, calls `ensureDefaultUsersExist()` in
   `run(String... args)`. No `@ConfigurationProperties`/`@EnableConfigurationProperties`
   wiring is needed since the seed values are fixed, not externally configurable. Let any
   persistence failure propagate so startup fails loudly rather than leaving the system
   without the expected admins.

3. **Tests**:
   - `InitialUserProvisioningServiceTest` (core-domain, unit test with mocked
     `UserRepository`/`PasswordEncoder`, no Spring context — mirror
     `ImportFolderSettingProvisioningServiceTest`'s style and `UserAdminServiceTest`'s
     `PasswordEncoder` mocking helper): covers — creates both `albert` and `oscar` when
     neither exists, each with `UserRole.ADMIN` and an encoded (not plaintext) password;
     creates only the missing one when the other already exists (per-user idempotency, not
     an all-or-nothing check); is a full no-op (no `save` calls) when both already exist;
     also skips a seed entry when its email is already taken by an unrelated account, even
     if the username doesn't match.
   - `InitialUserStartupInitializerTest` (api-runtime, mirror
     `ImportFolderSettingStartupInitializerTest`): verifies `run()` delegates to
     `InitialUserProvisioningService.ensureDefaultUsersExist()`, and a second test asserting
     the initializer does not swallow a persistence failure thrown by the service.

4. **Documentation**: add a bullet to the "Notes for a deployment target" section of
   `tt-data-league-api-runtime/README.md` (alongside the existing
   `ImportFolderSettingStartupInitializer` bullet) stating that
   `InitialUserStartupInitializer` seeds two fixed ADMIN accounts (`albert`, `oscar`) on
   first startup if they don't already exist by username/email, that their passwords are
   fixed in source (not env-configurable) and do not meet the normal password-strength
   rules enforced elsewhere, and that operators must change or remove these accounts before
   exposing any non-development environment.

# Implementation Guidelines

- The Goal/Acceptance Criteria specify two literal accounts (`albert`/`albert`,
  `oscar`/`Oscar&1234`), not a single configurable admin — the seed list is fixed in code,
  not sourced from `application.yml`/env vars. If a future feature needs operator-configurable
  seed accounts, that is a separate change, not part of this one.
- `UserValidator.validatePassword` requires length ≥ 8 plus uppercase, lowercase, digit, and
  a special character; `albert` (6 chars, no uppercase/digit/special) fails these rules.
  Because the seed passwords are dictated by the Acceptance Criteria verbatim, this service
  intentionally does not call `UserValidator`/`validateOrThrow` — it only encodes and saves.
  This is a deliberate scope decision, not an oversight; flag it as a security tradeoff in
  the PR description and in the README bullet from step 4.
- Reuse `existsByUsername`/`existsByEmail` (already on `UserRepository`) for idempotency,
  not `countActiveAdmins()` — the Acceptance Criteria call for these two specific accounts
  to exist, independent of whether some other admin already exists.
- Keep provisioning logic in `core-domain` (`InitialUserProvisioningService`), not in the
  runtime module, consistent with `ImportFolderSettingProvisioningService` living in
  `core-domain` while its `CommandLineRunner` wrapper lives in `api-runtime`.
- Do not touch `UserRegistrationService` or `UserAdminService` — this is a separate,
  narrowly-scoped startup concern, not a change to interactive registration/admin flows.
- Out of scope: making the seed list configurable, a UI/API to reconfigure the seeded
  admins, and automatic password rotation/expiry for the seeded accounts.

# Notes

- `User.createNew` defaults new users to `Set.of(UserRole.PRACTITIONER)`; the provisioning
  service must explicitly call `setRoles(Set.of(UserRole.ADMIN))` before saving.
- Original plan draft (single configurable admin via `InitialAdminUserProperties` +
  `${INITIAL_ADMIN_USERNAME:admin}`-style env vars) was replaced because it didn't match the
  Goal/Acceptance Criteria, which specify two named accounts with fixed credentials. Decision
  confirmed with the user on 2026-09-09: rewrite the plan to match the goal literally rather
  than generalize to a configurable list.
- Hardcoding real-looking, weak, non-env-overridable passwords in source is a known security
  tradeoff, accepted here because the Acceptance Criteria mandate these exact values; the
  README documentation step exists specifically to warn operators to change/remove these
  accounts outside development.
- Implemented 2026-09-09: `InitialUserProvisioningService` (core-domain) and
  `InitialUserStartupInitializer` (api-runtime, `CommandLineRunner`) added exactly as
  planned above; both seed users skip validation via `UserValidator` intentionally. Added
  `InitialUserProvisioningServiceTest` (4 tests) and `InitialUserStartupInitializerTest` (2
  tests), all passing. Documented the two seeded accounts in
  `tt-data-league-api-runtime/README.md`'s "Notes for a deployment target" section. Full
  reactor `mvn compile` succeeds; `tt-data-league-core-repository-jpa`'s test suite (a
  different module, untouched by this feature) fails in this sandbox due to a pre-existing,
  unrelated Testcontainers/DB-context issue — reproduced identically on `main` before this
  change, so it is not a regression from this work.
