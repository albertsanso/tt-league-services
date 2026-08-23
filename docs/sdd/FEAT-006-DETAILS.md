# Build Plan

1. **Confirm the rename boundary and inventory references.**
   - Rename the season-independent domain entity from `Player` to
     `FederatedPlayer`, including entity-facing repository ports, events,
     application commands/queries/handlers, and direct read-model types.
   - Keep the existing `...domain.player`, `...repository.jpa.player`, and
     `...api.rest.player` package paths. Preserve player routes, request
     parameters, response field names, and source-specific import terminology.
   - Do not rename `PlayerSeason` or mechanically rename every `player_id`.
     `PlayerSeason` is the season-registration identity, while lineup, game,
     and doubles-pair player columns reference that registration.
   - Search Java, resources, tests, documentation, and deployment assets for
     `Player`, `PlayerJPA`, `player_id`, `playerId`, and
     `@Param("playerId")` before editing, classifying each occurrence by its
     target entity.

2. **Rename the core domain model and contracts.**
   - Rename `Player.java` to `FederatedPlayer.java` and update its factories,
     event publication, imports, and event payload types.
   - Rename the canonical-player repository port and its entity-specific
     methods, then update all player application handlers, commands, queries,
     read models, events, and tests.
   - Update `PlayerSeason`'s canonical-player type and association methods to
     use `FederatedPlayer`, while preserving season, licence, registration UUID,
     optional association, and lineup references.
   - Keep UUID identity, source scoping, name modification behavior, and
     consolidation semantics unchanged.

3. **Remove identity-unsafe exact lookups.**
   - Replace unscoped exact `findPlayerByName` usage in creation and query
     flows with a source-scoped or explicitly disambiguated operation.
   - Do not add a `(source, name)` uniqueness constraint to the canonical
     player table: the current model does not declare that constraint, and
     `PlayerSeason` carries season-specific licence identity.
   - Do not silently select the first canonical player when source and name
     still identify more than one row. Preserve fragment search as a deliberate
     list/search operation rather than an exact entity-resolution shortcut.

4. **Update the JPA persistence adapter without changing package paths.**
   - Rename `PlayerJPA` to `FederatedPlayerJPA`, all canonical-player mappers,
     repository helpers/adapters, and corresponding tests.
   - Map the entity to table `federated_player`, preserving UUID, source, name,
     nullability, indexes, and the current absence of a source/name unique
     constraint.
   - Update `PlayerSeasonJPA`, its mappers, and repository helpers so the
     canonical association uses `FederatedPlayerJPA`, a
     `federatedPlayer` property where entity naming requires it, and the
     `federated_player_id` join column/index.
   - Update derived method paths and JPQL from `player`/`playerId` to
     `federatedPlayer`/`federatedPlayerId` only when they refer to the
     canonical entity.
   - Leave lineup, game, doubles-pair, match history, natural keys, cascades,
     and their `PlayerSeason` foreign keys untouched.

5. **Propagate the type rename through import, runtime, and REST wiring.**
   - Update import processors, consolidation values, in-memory repositories,
     and import tests to consume the renamed canonical-player port and entity.
   - Preserve source-specific reconciliation, optional `PlayerSeason`
     associations, licence handling, and canonical UUID selection.
   - Update runtime dependency injection and runners only where type names
     change.
   - Update REST adapters, DTO conversions, and tests for renamed internal
     types while preserving `/player` routes and JSON compatibility.

6. **Provide a safe existing-database migration.**
   - Confirm the deployment-owned versioning location because this repository
     currently has no Flyway, Liquibase, or other versioned migration
     mechanism.
   - Add or update the agreed migration to rename `player` to
     `federated_player` and `player_season.player_id` to
     `player_season.federated_player_id`, including applicable indexes,
     foreign-key names, and constraints.
   - Do not rename `lineup.player_id`, `game.home_player_id`,
     `game.away_player_id`, or `doubles_pair.player_id`; those columns target
     `PLAYER_SEASON`.
   - Preserve UUIDs, source/name values, row counts, registration metadata,
     and all historical references. Add existence, row-count, duplicate, and
     foreign-key integrity checks where the deployment migration mechanism
     supports them.
   - Do not treat Hibernate `ddl-auto: update` as a rename migration.
     Document the required migration-before-start sequence.

7. **Update schema and operational documentation.**
   - Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` to
     describe `FEDERATED_PLAYER`, `PLAYER_SEASON.federated_player_id`, and
     the preserved PlayerSeason-targeting `player_id` columns.
   - Update relevant runtime, API, and migration documentation while
     preserving public player routes and payload names.
   - Synchronize this file, `FEATURES.md`, acceptance criteria, and migration
     links once the deployment migration owner and location are confirmed.

8. **Add focused verification and run the reactor.**
   - Extend domain tests for renamed factories, contracts, association methods,
     and source-aware exact resolution.
   - Extend JPA integration tests for the `federated_player` table,
     `PlayerSeason` canonical-player round trips, nullable associations, and
     unchanged PlayerSeason foreign keys.
   - Update import, runtime, and REST tests for renamed types, source isolation,
     consolidation behavior, and unchanged endpoint payloads.
   - Run the focused module tests and the full `mvn test` reactor.

# Implementation Guidelines

- Prefer explicit renames and compiler-guided call-site updates over
  compatibility aliases that leave the old canonical entity type active.
- Keep domain code free of Spring/JPA concerns and keep import code free of
  persistence-adapter dependencies.
- Do not mechanically rename the `player` package, `PlayerSeason`, source
  terminology, public `/player` URL, or foreign keys that target
  `PlayerSeason`.
- Do not add `externalId` to `FederatedPlayer`, `PlayerSeason`, or related
  entities. Federation licences remain season-registration identity.
- Reuse existing repository ports, mappers, source conversion, and in-memory
  test repositories.
- Surface missing source scope or ambiguous exact names as explicit failures
  or warnings; never resolve an ambiguous row by choosing the first result.
- Preserve nullable canonical-player associations and all season-specific
  history and lineup references.

# Notes

- The current canonical JPA entity is `PlayerJPA` mapped to `player`.
  `PlayerSeasonJPA.player` is the direct canonical-player association and is
  the only `player_id` relationship in the current model that targets the
  season-independent Player table.
- The current `PLAYER` schema has no `(source, name)` unique constraint.
  `PLAYER_SEASON` carries source- and season-specific registration identity,
  so this feature must not copy the Club rename's uniqueness requirement.
- The repository currently has no versioned migration framework or migration
  location. The feature cannot be marked `ready` for implementation until the
  deployment owner confirms where the reviewed database migration will live
  and how it will be applied.
- The application, persistence mapping, import, REST, tests, and schema
  contract portions are implemented. The feature remains blocked until the
  deployment owner provides the migration location and application process for
  existing databases.
