# Build Plan

1. **Confirm identity, compatibility, and migration contracts.**
   - Inventory the current `FederatedClub` domain, JPA, import, API, frontend,
     test, and schema references before editing.
   - Confirm whether existing `/club/{id}` routes and API payloads continue to
     expose `FederatedClub` identifiers or move to canonical `Club` UUIDs.
     If identifiers change, define an explicit compatibility strategy.
   - Define how globally unique canonical names are established when existing
     sources contain duplicate or differently spelled names.
   - Establish the owner and location of the existing-database migration.
     This repository currently has no Flyway, Liquibase, or equivalent
     versioned migration directory.

2. **Add the canonical domain model and contracts.**
   - Add a season-independent `Club` under
     `../../../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/club`
     with UUID identity, validated name, factories, events, and a
     `ClubRepository` port.
   - Extend `FederatedClub` with its canonical `Club` association while
     retaining source and source-provided name as source-specific identity.
   - Update `Team`, club commands and queries, read models, events, and
     application handlers where the operation is canonical-club-facing.
   - Keep source-scoped import lookups on `FederatedClub`; never resolve an
     import record by an unscoped name when source identity matters.

3. **Implement JPA mappings, repositories, and migration support.**
   - Add `ClubJPA` mapped to `club` with the approved canonical-name
     uniqueness constraint and update club mappers, helpers, repositories,
     JPQL, and `@Query` paths.
   - Update `FederatedClubJPA` with a lazy `club_id` association and preserve
     existing UUIDs, source values, source names, and nullable legacy data
     until migration is complete.
   - Preserve `Team.federated_club_id` and all team, match, lineup, and
     season-registration foreign keys unless the approved identity design
     explicitly changes them.
   - Add the reviewed migration in the deployment-owned location. Include
     prechecks and postchecks for null names, duplicate canonical names,
     row counts, UUID preservation, foreign keys, and existing associations.
     Do not treat Hibernate schema update as a rename or data migration.
   - Update
     `../../../../tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for the
     `club` table, `federated_club.club_id`, constraints, and relationship
     behavior.

4. **Link imports and consolidation without losing historical identity.**
   - Update the RFETM, BCNESA, and FCTT club/team import processors to
     resolve or create a source-scoped `FederatedClub` linked to a canonical
     `Club`, preserving each source's existing identity rules and idempotency.
   - Update in-memory repositories, import wiring, and adapters together with
     the changed ports.
   - Rework
     `tt-data-league-import/.../shared/club/consolidate/TeamToClubConsolidationProcessor`
     to consolidate canonical associations only after successful source
     traversal, using a complete source-scoped inventory.
   - Keep consolidation opt-in and non-destructive. Preserve team IDs,
     season-specific names, match history, lineup references, and
     season-registration identity. Support write and report paths through the
     same matching analysis; do not enable RFETM without a source-specific
     identity policy.

5. **Propagate the contract through APIs and runtime clients.**
   - Update REST controllers, DTOs, competition responses, API mappers, and
     tests so canonical and federated identities are explicit while approved
     route and JSON compatibility is preserved.
   - Update frontend club API normalization, hooks, navigation, and detail
     pages only if the approved identifier or payload contract changes.
   - Update import-runtime wiring, command usage, and operational
     documentation for any new migration or consolidation invocation.

6. **Add focused verification and complete documentation.**
   - Test domain factories, validation, canonical/federated associations, and
     source-scoped resolution.
   - Test JPA round trips, uniqueness, foreign keys, canonical linking, and
     unchanged team/match/lineup references.
   - Test import linking, source isolation, idempotent repeat imports,
     consolidation matching, conflict handling, report mode, and opt-in
     runtime behavior.
   - Reconcile
     `docs/implementation-plans/consolidate-unique-club-processor.md` with
     the canonical `Club`/`FederatedClub` model so it does not describe an
     incompatible direct ownership model.
   - Run the focused module tests and then the full `mvn test` reactor. If the
     frontend contract changes, run its existing install, lint, and build
     commands from `tt-data-league-frontend`.

# Implementation Guidelines

- Keep dependencies directed inward: domain code must not depend on Spring or
  JPA, and import code must not depend on persistence adapters.
- Do not add `externalId` to `Club`, `FederatedClub`, `Team`, or related
  entities. Source-system identifiers remain in source-specific import or
  season-registration identity handling.
- Reuse existing repository ports, factories, mappers, matching policies, and
  in-memory implementations before introducing new abstractions.
- Keep canonical consolidation source-scoped, opt-in, deterministic,
  idempotent, and non-destructive. Never retarget match or lineup foreign keys.
- Surface invalid names, ambiguous matches, missing source scope, migration
  conflicts, and persistence failures explicitly; do not choose the first
  matching row or silently fall back to another source.
- Preserve public APIs where possible. Any intentional identifier or payload
  change requires coordinated backend, frontend, test, and documentation
  updates.

# Approved Decisions

- Existing REST route identifiers (`/api/v1/club/{id}`, `/find_by_id`,
  competition routes, and PUT) and the existing DTO `id` remain
  `FederatedClub` UUIDs. Canonical Club UUID/name values are additive fields.
- Canonical cross-source linking uses exact canonical display names only.
  Existing source-scoped FederatedClub consolidation, including its
  source-specific fuzzy/rules behavior, remains unchanged and non-destructive.
- This repository owns the manually applied PostgreSQL migration under
  `docs/migrations/FEAT-00008-canonical-club.sql`. It includes prechecks,
  backfill-safe nullable steps, and preservation/integrity postchecks.
  Hibernate `ddl-auto: update` is not a migration.

# Notes

- The historical implementation blocker was resolved by the approved
  decisions above; the migration is manually applied before runtime startup.
- The existing consolidation design is related but not a substitute for this
  feature: it repairs source-scoped historical associations and must not
  replace season-specific team or match identity.
- Existing source-specific rules and the opt-in consolidation behavior
  described in
  `docs/implementation-plans/consolidate-unique-club-processor.md` remain
  applicable unless an explicit FEAT-00008 decision supersedes them.
