# Rename `ClubSeason` to `Team` implementation plan

## Goal and terminology boundary

Rename the season-specific `ClubSeason` concept to `Team` throughout the
repository. `Club` remains unchanged: it is the source-scoped,
season-independent club identity. `Team` is the season-specific registration
that has a name, source, season, and optional `Club` association.

This is a coordinated public API, persistence, import, and documentation
refactor. It must not merge rows, change UUIDs, add external identifiers, or
change the meaning of match and lineup history. Existing `ClubSeason` rows
become `Team` rows with the same IDs and data.

## Scope and invariants

1. Rename Java types, packages, methods, fields, builders, commands, queries,
   events, repository ports, Spring Data helpers, mappers, processors, runners,
   tests, fixtures, and prose references from `ClubSeason` to `Team`.
2. Rename persistence terminology consistently:
   - `ClubSeasonJPA` becomes `TeamJPA`.
   - `club_season` becomes `team`.
   - `club_season_id` and `clubSeason` references become `team_id` and `team`
     where they describe the season-specific row.
   - Match foreign keys become `home_team_id`, `away_team_id`, and
     `winner_team_id`; lineup `club_id` becomes `team_id`.
   - Indexes, unique constraints, foreign keys, JPA relationship fields, and
     schema documentation use the new names.
3. Preserve all UUIDs, source values, season values, names, `club_id`
   associations, match natural keys, lineup assignments, and registration
   identity.
4. Keep `Club` and `Team` distinct. Do not rename the `CLUB` table, introduce
   `externalId` fields on either entity, or replace a `Team` reference with a
   `Club` reference in `Match` or `Lineup`.
5. Preserve source scoping and the existing import identity policies. A
   terminology change must not turn a source-scoped lookup into an unscoped
   name lookup.
6. Treat the rename as a breaking internal public-API change and update every
   in-repository consumer in the same change. Do not leave deprecated
   `ClubSeason` aliases if the repository-wide zero-reference criterion is
   required.

## Current reference inventory

The implementation should begin with a repository-wide case-sensitive and
case-insensitive search for `ClubSeason`, `clubSeason`, `CLUB_SEASON`,
`club_season`, and related hyphenated forms. The known application surfaces
include:

- **Domain:** `domain/club/model/ClubSeason`, `ClubSeasonRepository`,
  club-season create/find/update/delete commands and handlers, and
  `ClubSeason*Event` classes.
- **Relationships:** `Match` home/away/winner fields and builders, `Lineup`
  team association and builder, plus their event and repository consumers.
- **JPA:** `ClubSeasonJPA`, its repository helper and adapter, both mappers,
  match and lineup JPA relationships/mappers, schema/index/constraint names,
  and `ImportSchemaTest`.
- **Import:** RFETM, BCNESA, and FCTT club/match processors, the shared
  `ClubSeasonConsolidationProcessor`, its summary/report types and tests, and
  `InMemoryRepositories`.
- **Runtime:** `ClubConsolidationRunner`, consolidation wiring, logging, and
  any CLI or usage text referring to club-season consolidation.
- **APIs and docs:** REST, GraphQL, runtime, module README, implementation
  plans, prompts, comments, and schema documentation. Current API modules do
  not appear to expose `ClubSeason`, but their source and generated-contract
  references must still be searched and verified.

Generated `target/` output is not source scope and must not be edited or added.

## Domain and application-layer changes

1. Move/rename `ClubSeason` to `Team` in the existing club domain area, keeping
   the package boundary unless a package move is needed for consistency.
   Rename factories (`createNew`, `createExisting` return types), getters,
   `withClub`, and all constructor-local variables to the new terminology.
2. Rename `ClubSeasonRepository` to `TeamRepository` and rename every method
   to use `Team`, including ID, name/season/source lookup, inventory, save,
   delete, and similar-name operations. Keep signatures source-scoped where
   they are today.
3. Rename create, find, modify-name, and delete command/query classes and
   handlers. Preserve validation, event publication, natural-key behavior,
   and error handling.
4. Rename `ClubSeasonCreatedEvent`, `ClubSeasonNameModifiedEvent`, and
   `ClubSeasonDeletedEvent` to `Team*Event`, updating event factories and
   subscribers. Event payload semantics remain unchanged apart from the type
   name.
5. Update `Match` and `Lineup` to expose `Team` relationships and intent-revealing
   names such as `homeTeam`, `awayTeam`, `winnerTeam`, `team`, and
   `getTeam()`. Preserve the existing aggregate fields and builder behavior.
6. Update domain tests and all test fixtures so they assert the same IDs,
   seasons, source values, and relationships after the type rename.

## JPA adapter and database migration

1. Rename the JPA model to `TeamJPA`, update its package/imports, table name,
   indexes, unique constraint names, and `club_id` relationship field.
2. Rename repository helper methods and Spring Data query methods from
   club-season terminology to team terminology. Update `TeamRepositoryJpa`
   to implement `TeamRepository`, retaining explicit `ImportSource` to JPA
   `Source` conversion and source-scoped queries.
3. Rename both team mappers and update every mapper that traverses a team
   relationship, especially `Match` and `Lineup`. Ensure nullable `Club`
   handling and lazy associations remain unchanged.
4. Add a versioned, reviewed database migration or equivalent deployment
   procedure that renames tables and columns rather than dropping and
   recreating them:
   - rename `club_season` to `team`;
   - rename match and lineup foreign-key columns;
   - rename indexes and constraints where supported;
   - drop/recreate foreign keys only as required by the table/column rename;
   - preserve all row IDs and data;
   - verify row counts and foreign-key references before and after migration.
5. Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` as the
   schema contract. Document `TEAM`, its columns and constraints, and all
   `MATCH`/`LINEUP` foreign-key targets using team terminology. Record the
   migration requirement and do not silently claim that an application-only
   rename migrates an existing database.
6. Do not alter match or lineup natural-key constraints, cascade behavior,
   nullability, or historical references except for the required identifier
   names.

## Import and consolidation changes

1. Rename the shared consolidation processor and all report/result values from
   `ClubSeasonConsolidationProcessor` to team terminology. Keep the processor
   source-scoped, opt-in, and post-traversal.
2. Update RFETM, BCNESA, and FCTT import processors and match processors to use
   `Team`, `TeamRepository`, and team-oriented method names. Preserve each
   source's existing key and normalization rules; do not replace source
   identity with a generic team-name lookup.
3. Update `InMemoryRepositories` and all import tests to implement the renamed
   port and retain idempotent natural-key behavior.
4. Keep consolidation semantics unchanged: it may re-associate a `Team` with a
   canonical `Club`, but it must not delete or merge team registrations and
   must not retarget match or lineup foreign keys.
5. Rename import package paths only where required by the type rename, and
   update all imports rather than leaving compatibility wrappers that preserve
   the old terminology.

## Runtime and API/documentation changes

1. Rename runtime runner, injected processor types, variables, logs, and usage
   text from club-season terminology to team terminology. Preserve the existing
   `--consolidate-clubs` option because it describes the club consolidation
   action, not the name of the registration entity.
2. Update runtime tests to prove traversal, optional consolidation, source
   selection, report mode, and transaction behavior remain unchanged.
3. Search REST, GraphQL, and API runtime modules for Java, JSON, schema,
   endpoint, DTO, query, and generated-document references. If a
   `ClubSeason` API surface exists, rename it to `Team` and update its route or
   schema field only with an explicit compatibility/migration decision. If no
   surface exists, record that no API change is needed rather than inventing
   one.
4. Update module READMEs, implementation plans, comments, diagrams, prompts,
   examples, and operational documentation. Do not update generated build
   output or create new planning/tracking documents beyond this requested
   plan.

## Tests and verification

Add or update focused tests for:

1. Domain factories, immutable association replacement, renamed commands,
   queries, handlers, and event payloads.
2. Match and lineup construction using `Team`, including preservation of
   references and builder behavior.
3. JPA mapping and repository queries, including table/column names,
   source-scoped lookup, nullable club association, and persistence/reload.
4. A migration/integration fixture that verifies row counts, UUIDs, team data,
   match foreign keys, lineup foreign keys, unique constraints, and
   idempotent reload after the rename.
5. RFETM, BCNESA, and FCTT import processors and consolidation behavior,
   including source isolation and no match/lineup retargeting.
6. Runtime wiring and any affected REST/GraphQL contract tests.
7. A repository-wide search that confirms no source or documentation reference
   remains for `ClubSeason`, `clubSeason`, `CLUB_SEASON`, or `club_season`
   except explicitly documented historical migration notes or the source
   prompt, if those are intentionally retained.

Run focused tests as each boundary changes:

```text
mvn -pl tt-data-league-core-domain -am test
mvn -pl tt-data-league-core-repository-jpa -am test
mvn -pl tt-data-league-import -am test
mvn -pl tt-data-league-import-runtime -am test
```

Finish with the full reactor:

```text
mvn test
```

## Acceptance criteria

1. All application source, tests, public in-repository APIs, package/type
   names, persistence mappings, and documentation use `Team` terminology.
2. `Club` remains a separate entity and no external identifiers are added.
3. The database migration preserves existing team/registration UUIDs and data,
   renames all required tables and foreign-key columns, and keeps constraints
   and historical references valid.
4. Match and lineup domain and persistence relationships point to `Team`
   without changing their historical identity or natural keys.
5. Import, consolidation, runtime, and API behavior remains equivalent except
   for the intentional terminology/API rename.
6. Focused tests and the full Maven reactor pass, and the final search shows
   no unintended old terminology.

## Implementation order

1. Confirm the complete reference inventory and migration mechanism.
2. Rename domain types, ports, commands, queries, events, and relationship
   models, then pass the domain test suite.
3. Rename JPA entities, repositories, mappers, schema documentation, and
   migration, then pass repository integration tests.
4. Rename import processors, consolidation code, in-memory repositories, and
   tests.
5. Rename runtime wiring and verify API surfaces and documentation.
6. Run the full reactor, perform the zero-reference search, and review the
   final diff for generated files, secrets, and unrelated changes.

## Open decisions before implementation

1. Which migration mechanism and deployment versioning convention should carry
   the existing `club_season` table and foreign-key column renames?
2. Are external API consumers required to receive a compatibility period for
   any discovered `ClubSeason` endpoint or schema field, or is the rename
   intentionally breaking?
3. Should historical migration documentation retain the literal old table/type
   names for operational traceability, or must the zero-reference rule remove
   them there as well?
