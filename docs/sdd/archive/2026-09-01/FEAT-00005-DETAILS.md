# Build Plan

1. **Confirm the rename boundary and inventory references.**
   - Rename entity-oriented Java symbols from `Club` to `FederatedClub`,
     including the domain entity, repository port, entity events, application
     commands/queries/handlers/read models, and `Team`'s association methods.
   - Keep the existing `...domain.club`, `...repository.jpa.club`, and
     `...api.rest.club` package paths. Do not add external identifiers or
     change UUID/source/name identity semantics.
   - Preserve the public HTTP contract (`/club`, existing route parameters,
     permissions, and JSON field names) unless an API compatibility review
     explicitly approves a breaking change. Java controller/DTO names may be
     renamed when they directly expose the entity type.
   - Search Java, resources, tests, documentation, and deployment assets for
     `Club`, `ClubJPA`, `club_id`, `clubId`, and `@Param("clubId")` before
     editing, then repeat the search as a completion gate.

2. **Rename the core domain model and contracts.**
   - Rename `Club.java` to `FederatedClub.java` and update its factories,
     event publication, imports, and event payload types.
   - Rename `ClubRepository` and its methods to entity-specific
     `FederatedClub` names, then update all application handlers, commands,
     queries, read models, events, and tests under
     `tt-data-league-core-domain`.
   - Update `Team` to expose an `Optional<FederatedClub>`, including its
     factory, replacement/association method, getter, and `TeamCreatedEvent`
     payload. Keep team IDs, seasons, names, match history, and lineup
     references unchanged.

3. **Remove identity-unsafe unscoped exact lookups.**
   - Replace `findByName`/`findClubByName` usage in creation and query flows
     with source-scoped `(source, name)` lookup. The create command already
     carries a source and must use it when checking duplicates.
   - Change `FindClubByNameQuery` and its handler to require source, or remove
     the unused exact-name query if no caller needs it. Do not silently pick a
     row when two federations share a name.
   - Retain multi-source fragment search only as a deliberate list/search
     operation; ensure exact entity resolution remains source-scoped and the
     `(source, name)` uniqueness constraint is enforced.

4. **Update the JPA persistence adapter without changing package paths.**
   - Rename `ClubJPA` to `FederatedClubJPA`, all club mappers, repository
     helpers/adapters, and corresponding tests.
   - Map the entity to table `federated_club`; rename related index and unique
     constraint identifiers consistently while preserving the `(source, name)`
     constraint.
   - Update `TeamJPA`, its mappers and repository helpers to use the
     `federatedClub` association and `federated_club_id` join column/index.
     Update derived method paths and JPQL from `club`/`clubId` to
     `federatedClub`/`federatedClubId`, including
     `@Param("federatedClubId")`.
   - Leave `MATCH` and `LINEUP` team foreign keys, natural keys, cascades, and
     historical references untouched.

5. **Propagate the type rename through import and runtime wiring.**
   - Update import processors, consolidation support values, in-memory
     repositories, and import tests to consume the renamed domain port and
     entity while preserving source-specific identity policies.
   - Update import-runtime dependency injection, runners, argument handling,
     and tests only where type names change; preserve opt-in consolidation,
     report-mode behavior, source scoping, and post-traversal sequencing.
   - Update REST adapters and tests to use the renamed internal types while
     preserving `/club` endpoints and response compatibility.

6. **Provide a safe existing-database migration.**
   - Confirm the deployment-owned versioning location because this repository
     currently has no Flyway/Liquibase or other versioned migration mechanism.
     Do not introduce a new migration dependency in the parent POM as part of
     this rename.
   - Add or update the agreed versioned SQL/deployment migration to rename
     `club` to `federated_club` and every actual `club_id` foreign-key column
     to `federated_club_id` (currently `team.club_id`), including indexes,
     foreign-key and unique-constraint names where supported.
   - The migration must be ordered and transactional where the database
     supports it, preserve UUIDs, source/name values, row counts and
     references, and fail rather than recreate or drop data. Add pre/post
     checks for table/column existence, duplicate `(source, name)` rows,
     row counts, and foreign-key integrity.
   - Keep Hibernate `ddl-auto: update` from being treated as a rename
     migration; document the required migration-before-start sequence.

7. **Update schema and operational documentation.**
   - Update `../../../../tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` to use
     `FEDERATED_CLUB`, `federated_club_id`, the renamed indexes/constraints,
     and the actual migration scope.
   - Update `../../../../tt-data-league-import-runtime/README.md` and relevant API
     documentation with the migration prerequisite, while preserving external
     route names. Review existing implementation-plan references and retain
     historical terminology only where it documents an earlier schema.
   - Synchronize this file, `../../FEATURES.md`, acceptance criteria, and any
     migration/deployment links once the migration owner is confirmed.

8. **Add focused verification and run the reactor.**
   - Extend domain tests for source-scoped duplicate detection and renamed
     factories/contracts.
   - Extend JPA integration tests for the `federated_club` table,
     `(source, name)` uniqueness, `federated_club_id` team association, and
     persistence round trips.
   - Update import and REST tests for renamed types, source isolation, and
     unchanged endpoint payloads; add runtime wiring coverage if bean or
     argument surfaces change.
   - Run `mvn -pl tt-data-league-core-domain -am test`,
     `mvn -pl tt-data-league-core-repository-jpa -am test`,
     `mvn -pl tt-data-league-import -am test`,
     `mvn -pl tt-data-league-import-runtime -am test`, and `mvn test`.

# Implementation Guidelines

- Prefer explicit renames and compiler-guided call-site updates over
  compatibility aliases that leave the old entity type active.
- Keep domain code free of Spring/JPA concerns and keep import code free of
  persistence-adapter dependencies.
- Do not mechanically rename the `club` package, source-specific club
  terminology, or the public `/club` URL. Rename entity identity symbols and
  persistence identifiers only where the feature requires it.
- Reuse the existing repository ports, mappers, source conversion, and
  in-memory test repositories. Do not add `externalId` to `FederatedClub`,
  `Player`, or `Team`.
- Preserve nullability and lazy relationship behavior currently documented by
  the JPA schema. Do not redirect match or lineup foreign keys to a club row.
- Surface migration and lookup ambiguity as explicit failures or warnings;
  never resolve an unscoped exact name by choosing the first row.
- Keep edits limited to FEAT-00005 and preserve unrelated worktree changes.

# Notes

- `../../FEATURES.md` is the authoritative registry and should move FEAT-00005 from
  `idea` to `planned` now that this build plan exists. It remains in Backlog;
  implementation should wait until the plan is approved and the migration
  ownership/location is decided.
- The current JPA schema has `CLUB` plus `TEAM.club_id`; current `MATCH` and
  `LINEUP` relationships point to `TEAM`, not `CLUB`. The migration must not
  apply the unrelated historical `club_season`/match/lineup renames described
  in older implementation notes.
- The current repository exposes `findClubByName` and the create handler uses
  it despite the `(source, name)` uniqueness rule. This is a correctness issue
  in the FEAT-00005 scope, not a reason to add a second uniqueness mechanism.
- No versioned migration scripts or migration framework were found in the
  repository. FEAT-00005 cannot be marked `ready` until the deployment owner
  confirms where the reviewed migration will live and how it will be applied.
- Implementation was authorized without a deployment migration; the code and
  schema contract now target `federated_club` and `federated_club_id`, but the
  feature remains blocked until the deployment owner supplies and verifies the
  existing-database migration.
