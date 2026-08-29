# Build Plan

1. **Confirm player identity, compatibility, and migration contracts.**
   - Inventory the current `FederatedPlayer` domain, JPA, import, API,
     frontend, test, and schema references before editing.
   - Confirm that existing player routes, API payloads, and identifiers continue
     to expose `FederatedPlayer` UUIDs. Canonical `Player` UUID/name values
     should be additive unless an explicit compatibility decision says
     otherwise.
   - Define how globally unique canonical names are established when existing
     sources contain duplicate, differently spelled, or blank names.
   - Confirm that the FEAT-00006 rename migration has been applied before this
     feature migration. This feature must not reintroduce the legacy `player`
     table as the federated entity.
   - Use the repository-owned manual PostgreSQL migration location
     `docs/migrations/FEAT-00009-canonical-player.sql`; Hibernate schema update is
     not a data migration.

2. **Add the canonical domain model and contracts.**
   - Add a season-independent `Player` under
     `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/player`
     with UUID identity, validated name, factories, events, and a
     `PlayerRepository` port.
   - Extend `FederatedPlayer` with its canonical `Player` association while
     retaining source and source-provided name as source-specific identity.
   - Update player commands, queries, read models, events, and application
     handlers where the operation is canonical-player-facing.
   - Keep `PlayerSeason` associated with `FederatedPlayer`; preserve its
     season, licence, registration UUID, optional association, and
     season-specific name.
   - Keep source-scoped import lookups on `FederatedPlayer`; never resolve an
     import record by an unscoped name when source identity matters.

3. **Implement JPA mappings, repositories, and migration support.**
   - Add `PlayerJPA` mapped to `player` with the approved canonical-name
     uniqueness constraint, then update player mappers, helpers, repositories,
     JPQL, and `@Query` paths.
   - Update `FederatedPlayerJPA` with a lazy nullable `player_id` association
     and preserve existing UUIDs, source values, and source names.
   - Preserve `PlayerSeason.federated_player_id` and all PlayerSeason, match,
     lineup, and doubles-pair foreign keys. Columns named `player_id`,
     `home_player_id`, and `away_player_id` that target `PlayerSeason` must not
     be redirected to canonical `Player`.
   - Add the reviewed manual migration at the agreed deployment location.
     Include prechecks and postchecks for null or blank names, duplicate
     canonical names, row counts, UUID preservation, foreign keys, and
     existing associations.
   - Backfill only by exact, case-sensitive canonical display name. Leave
     ambiguous or otherwise unresolvable rows explicitly unlinked for
     remediation; do not choose a first match.
   - Update
     `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for the
     `player` table, `federated_player.player_id`, constraints, indexes, and
     relationship behavior.

4. **Link imports and player-season consolidation without losing identity.**
   - Update RFETM, BCNESA, and FCTT player import processors to resolve or
     create a source-scoped `FederatedPlayer` linked to a canonical `Player`,
     preserving source-specific identity and idempotency.
   - Update in-memory repositories, import wiring, and adapters together with
     the changed ports.
   - Rework player-season consolidation to update only the
     `PlayerSeason`-to-`FederatedPlayer` association through the existing
     immutable operation. Do not delete registrations or retarget match,
     lineup, game, or doubles-pair foreign keys.
   - Run consolidation only after successful source traversal and against a
     complete source-scoped inventory, even when `--season` limits imported
     files.
   - Keep consolidation opt-in and non-destructive. The write and report paths
     must use the same matching analysis, and report mode must not persist
     changes.

5. **Propagate the contract through APIs and runtime clients.**
   - Update REST controllers, DTOs, player-season responses, API mappers, and
     tests so canonical and federated identities are explicit while preserving
     approved route and JSON compatibility.
   - Update frontend player API normalization, hooks, navigation, and detail
     pages only if the approved identifier or payload contract changes.
   - Update import-runtime wiring, command usage, and operational
     documentation for the migration and any consolidation invocation.

6. **Add focused verification and complete documentation.**
   - Test domain factories, validation, canonical/federated associations,
     source-scoped resolution, and ambiguous-name handling.
   - Test JPA round trips, canonical-name uniqueness, nullable foreign keys,
     exact-name linking, and unchanged PlayerSeason-targeting references.
   - Test import linking, source isolation, idempotent repeat imports,
     PlayerSeason consolidation, conflict handling, report mode, and opt-in
     runtime behavior.
   - Reconcile
     `docs/implementation-plans/player-season-consolidation-processor.md` and
     related player plans with the canonical `Player`/`FederatedPlayer` model.
   - Run the focused module tests and then the full `mvn test` reactor. If the
     frontend contract changes, run its existing install, lint, and build
     commands from `tt-data-league-frontend`.

# Implementation Guidelines

- Keep dependencies directed inward: domain code must not depend on Spring or
  JPA, and import code must not depend on persistence adapters.
- Do not add `externalId` to `Player`, `FederatedPlayer`, `PlayerSeason`, or
  related entities. Federation licences remain season-registration identity.
- Keep `Player` limited to UUID identity and canonical name. Keep source,
  source-provided names, and source-system identifiers in their existing
  source-specific or season-registration boundaries.
- Do not add a `(source, name)` unique constraint to `FederatedPlayer`.
  `PlayerSeason` owns source-, season-, and licence-specific registration
  identity.
- Reuse existing repository ports, factories, mappers, matching policies, and
  in-memory implementations before introducing new abstractions.
- Keep canonical linking exact-name, source-scoped, deterministic, idempotent,
  opt-in, and non-destructive. Report mode must perform the same analysis as
  write mode without persistence writes.
- Surface invalid names, missing source scope, ambiguous exact matches,
  migration conflicts, and persistence failures explicitly; never resolve an
  ambiguous row by choosing the first result.
- Preserve public player routes, response field names, nullable canonical
  associations, PlayerSeason identity, and all historical match and lineup
  references.

# Notes

- `FederatedPlayer` remains the source-dependent player identity. `Player` is
  the season-independent canonical identity referenced by it.
- The canonical `Player` name is globally unique and exact-name matching is the
  only automatic cross-source linking rule. The `(source, name)` index on
  `FederatedPlayer` remains non-unique and is used only for scoped lookup.
- The FEAT-00006 migration that renamed the legacy federated table and
  `PlayerSeason` canonical-player column is a prerequisite. This feature adds
  the canonical `Player` table and the `FederatedPlayer.player_id` association;
  it must not rename PlayerSeason-targeting `player_id` columns.
- The repository has no Flyway, Liquibase, or equivalent versioned migration
  framework. The reviewed PostgreSQL migration must be applied manually before
  the renamed application starts, with the existing datasource configuration.
- Existing player-season consolidation is related but is not a substitute for
  canonical linking. It repairs source-scoped historical associations and must
  not merge or delete season registrations or replace their historical
  references.
