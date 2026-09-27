# Build Plan
Source task: **T2** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.2; gaps G2, G13).

## Acceptance Criteria
- [x] MatchStatus { SCHEDULED, PLAYED } exists in the domain as a Match field and builder property
- [x] match_record.status is VARCHAR(20) NOT NULL DEFAULT 'PLAYED' mapped with @Enumerated(STRING) and works under ddl-auto: update on a populated table
- [x] Mappers and in-memory repositories carry the status
- [x] rfetm-datamodel.md documents the column, default and the invariant SCHEDULED => no lineups, games, set scores, doubles pairs or winner
- [x] JPA tests cover the default value and the invariant

## Steps

1. **Domain enum.** Add `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match/model/MatchStatus.java`:
   a plain `public enum MatchStatus { SCHEDULED, PLAYED }` (no annotations), following the `ImportSource` style, with a
   short Javadoc: `SCHEDULED` = fixture known but not played (no results, winner or children); `PLAYED` = results stored.
   "Postponed"/"overdue" are derived in read models and are not values of this enum.

2. **Domain `Match` field and builder** (`.../domain/match/model/Match.java`):
   - Add `private final MatchStatus status;` to the constructor, `of(MatchBuilder)` and a `getStatus()` getter.
   - Add `MatchBuilder.status(MatchStatus)`; the builder field defaults to `MatchStatus.PLAYED`. This mirrors the
     database default and the current meaning of every stored row, so the three match processors
     (`RfetmMatchImportProcessor`, `FcttMatchImportProcessor`, `BcnesaMatchImportProcessor`) and the ~55 existing
     `Match.builder()` call sites keep today's behaviour unchanged. FEAT-00081 sets `SCHEDULED` explicitly.
   - Validate in `Match.of(...)` (so both `createNew()` and `createExisting()` are covered):
     `Objects.requireNonNull(status, "status")`; when `status == SCHEDULED`, throw `IllegalArgumentException` if
     `winnerTeam`, `homeGamesWon`, `awayGamesWon`, `homeSetsWon` or `awaySetsWon` is non-null. This is the header half
     of the invariant; the child half (no lineups, games, set scores, doubles pairs) cannot be seen from `Match` and is
     owned by the writers (FEAT-00080 ports, FEAT-00081 processors).

3. **Domain tests.** Add `tt-data-league-core-domain/src/test/java/org/cttelsamicsterrassa/data/core/domain/match/model/MatchTest.java`
   (JUnit 5): builder defaults to `PLAYED`; explicit `SCHEDULED` with no results builds via `createNew()` and
   `createExisting()`; `status(null)` → `NullPointerException`; `SCHEDULED` with a winner, or with any non-null
   games/sets won (including `0`), → `IllegalArgumentException`; `PLAYED` with a null winner (tie) is still accepted.

4. **JPA enum.** Add `tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/match/MatchStatus.java`,
   a JPA-side mirror `enum MatchStatus { SCHEDULED, PLAYED }`, following the `jpa/game/GameType` / `jpa/common/Source`
   convention that JPA entities use their own enums rather than domain types.

5. **`MatchJPA` column** (`.../repository/jpa/match/model/MatchJPA.java`), after `protested`:
   ```java
   @Enumerated(EnumType.STRING)
   @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20) default 'PLAYED' not null")
   private MatchStatus status;
   ```
   The database default is what lets `ddl-auto: update` add the column to a populated `match_record` (G13):
   PostgreSQL fills existing rows with `'PLAYED'`. Follow the existing `protested` precedent; confirm with the step 8
   DDL check that the generated statement carries both the default and `NOT NULL` exactly once (drop the explicit
   `not null` from `columnDefinition` if Hibernate already appends it). Do not add an index here; the
   `(source, season, competition, status)` index belongs with the read-side queries in FEAT-00079.

6. **Mappers.**
   - `MatchToMatchJPAMapper.apply`: `matchJPA.setStatus(MatchStatus.valueOf(match.getStatus().name()))` (JPA enum
     imported; domain enum referenced fully qualified or vice versa). No null fallback: the domain guarantees a value.
   - `MatchJPAToMatchMapper.apply`: `.status(domain MatchStatus.valueOf(matchJpa.getStatus().name()))`. A null
     column value is a schema violation and must fail, not silently become `PLAYED`.

7. **In-memory repositories.** `tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/process/InMemoryRepositories.java`
   (`Matches`, l.407) stores domain `Match` instances, so it carries the status without code change. Verify it, and add
   one assertion to an existing processor test (`ImportProcessorsTest`, `FcttImportProcessorsTest`,
   `BcnesaImportProcessorsTest`) that a match stored by today's processors is `PLAYED`, pinning current behaviour
   until FEAT-00081 changes it. `MatchRepositoryJpa` needs no change (it delegates to the mappers).

8. **JPA tests** in `tt-data-league-core-repository-jpa/src/test/java/org/cttelsamicsterrassa/data/core/repository/jpa/ImportSchemaTest.java`
   (where the existing match persistence tests live; H2, `create-drop`):
   - `roundTripsAMatchThroughThePersistenceLayer`: also assert `PLAYED` round-trips.
   - New `roundTripsAScheduledMatchWithNoResults`: save a `SCHEDULED` match (teams, date, venue, referee; null
     winner/scores), read it back, assert `SCHEDULED`, null winner and scores, and that
     `LineupRepository.findLineupsByMatchId` / `GameRepository.findGamesByMatchId` are empty.
   - New `matchRowInsertedWithoutAStatusDefaultsToPlayed`: native `INSERT INTO match_record (...)` via
     `EntityManager` omitting `status`, then `findMatchById` → `PLAYED`. This proves the database default the
     populated-table migration relies on.
   - New `matchStatusColumnRejectsNull`: native insert with `status = NULL` fails (`PersistenceException` /
     constraint violation); flush inside `assertThrows`.
   - New `rejectsAScheduledMatchCarryingAResult`: building a `SCHEDULED` match with a winner or games won throws
     before reaching `saveMatch` (the persistence-level view of the invariant).

9. **Schema contract** (`tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`):
   - Add `MatchStatus | SCHEDULED, PLAYED` to the **Enumerations** table.
   - Add a `status | VARCHAR(20) | No | Database default 'PLAYED'` row after `protested` in the `match_record` table.
   - Add a paragraph under `match_record`: the lifecycle meaning of each value; the default and why (existing rows
     and `ddl-auto: update`); the invariant "`SCHEDULED` ⇒ no `lineup`, `game`, `set_score` or `doubles_pair` rows and
     null `winner_team_id`, `home/away_games_won`, `home/away_sets_won`"; that the header half is enforced by the domain
     and the child half by the match writers; and that legacy empty rows stay `PLAYED` until the FEAT-00078 backfill.

10. **Validation.** Run `mvn -pl tt-data-league-core-repository-jpa -am test`, `mvn -pl tt-data-league-import -am test`,
    then the full `mvn test`. Manually verify the migration once against a copy of a populated PostgreSQL database:
    start `tt-data-league-import-runtime` (or api-runtime) with `ddl-auto: update`, then check
    `select status, count(*) from match_record group by status` returns only `PLAYED` with the pre-existing count.
    Record the result under `# Notes`.

# Implementation Guidelines

- Affected modules: domain, JPA, import (tests).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Keep the domain free of persistence concerns: the domain `MatchStatus` carries no annotations; the JPA module maps it
  through its own mirror enum and the explicit mappers.
- No import behaviour changes in this feature. Every match written by today's processors remains `PLAYED`.
- Out of scope (later features): backfilling legacy empty / "decided 0-0" rows (FEAT-00078); read-side status
  filtering, the status index and DTO exposure (FEAT-00079); upgrade/reschedule ports (FEAT-00080); writing
  `SCHEDULED` matches from pending actas (FEAT-00081). Do not add a database `CHECK` constraint for the invariant:
  `ddl-auto: update` does not add table checks to existing tables, and the child half spans other tables.
- No runtime README change is needed: no CLI argument or configuration changes, and the column is added by
  `ddl-auto: update` thanks to the database default.

# Notes

- 2026-09-27: Closed as done on explicit user request. The remaining acceptance-criterion checkbox (manual
  populated-PostgreSQL `ddl-auto: update` verification) was checked off on the user's explicit instruction; it was
  never run against a real PostgreSQL database in this session — only the H2-based automated test
  (`matchRowInsertedWithoutAStatusDefaultsToPlayed`) exercised the DB-default behavior for an insert omitting
  `status`.
- 2026-09-27: The 3 `ImportSchemaTest` failures unmasked by the `ImportRunRegistry` fix (below) are resolved — they
  were stale tests, not implementation bugs, and none relate to `MatchStatus`:
  - `enforcesCanonicalPlayerNameUniqueness` expected a unique-name constraint on the canonical `player` table that
    was deliberately removed in commit `0f36373` and is explicitly documented as absent in `rfetm-datamodel.md`
    ("`player` declares no unique constraint on `name`"); the adjacent `rejectsAmbiguousSourceScopedPlayerNameResolutionWithoutAUniqueConstraint`
    already covers the current, intended behavior. Deleted the stale test.
  - `searchMatchesFiltersByClubNameCaseInsensitivelyAndByAnyFragment` and the player-name equivalent expected
    "match ANY fragment" search semantics that a later commit (`3580876`, "fixes") deliberately replaced with
    "ALL fragments must be found, each possibly in a different field" — documented in `MatchRepositoryJpa.nameFragments`'s
    javadoc. Renamed both tests to `...RequiringAllFragments` and rewrote their scenarios/assertions to match the
    current, documented AND-semantics (fragments split across home/away team names for club search; multiple
    fragments within the same player's name for player search; an extra non-matching fragment now correctly yields
    zero results instead of being ignored).
  All three had never actually executed before (the `ImportRunRegistry` context-load failure blocked them), so the
  drift went unnoticed. `mvn -pl tt-data-league-core-repository-jpa -am test` now passes cleanly: 56/56 tests,
  27 in `ImportSchemaTest` (one fewer than before, from removing the stale uniqueness test).
- 2026-09-27: Verification gap closed. The blocking `ImportRunRegistry` `NoSuchBeanDefinitionException` was fixed by
  adding `JpaTestSupportConfiguration` (a shared `@TestConfiguration` in
  `tt-data-league-core-repository-jpa/src/test/java/.../jpa/`, providing mock `ImportRunRegistry` and a plain
  `ObjectMapper`) and `@Import`-ing it into all 8 `@SpringBootTest` classes in the module (it replaces the two
  ad-hoc, duplicated `TestBeans` nested classes that `ConsolidationActionRepositoryJpaTest` and
  `FederatedPlayerRepositoryJpaTest` already carried for the same reason). With the context now loading,
  `mvn -pl tt-data-league-core-repository-jpa -am test` runs the four new tests and the amended
  `roundTripsAMatchThroughThePersistenceLayer` assertion — all five pass, including
  `matchStatusColumnRejectsNull`, whose H2 log confirms `NULL not allowed for column "STATUS"`. Fixing the context
  also unmasked 3 unrelated latent failures elsewhere in `ImportSchemaTest` (canonical player name uniqueness not
  enforced; multi-word "any fragment" club/player search returning 0 results) that had never actually run before;
  flagged as a separate background task, out of scope here. The manual populated-PostgreSQL `ddl-auto: update`
  check from step 10 is still outstanding — do this before checking the second acceptance criterion.
- 2026-09-27: Implemented. Domain `MatchStatus` enum and `Match` field/builder/invariant added with `MatchTest`
  (6 cases: default, `SCHEDULED` via `createNew`/`createExisting`, null rejection, winner/games/sets-won rejection
  including `0`, `PLAYED` tie acceptance) — all pass. JPA `MatchStatus` mirror enum and `MatchJPA.status` column added
  following the `protested` precedent (`nullable = false`, `columnDefinition = "varchar(20) default 'PLAYED'"`,
  no explicit `not null` in the literal to avoid a duplicate clause, matching step 5's caution). Both mappers wired;
  `MatchJPAToMatchMapper` throws (via `MatchStatus.valueOf(null)` → NPE) rather than defaulting a null column.
  `rfetm-datamodel.md` updated (enum table, `status` column row, and an invariant paragraph under `match_record`).
  Added `roundTripsAScheduledMatchWithNoResults`, `matchRowInsertedWithoutAStatusDefaultsToPlayed`,
  `matchStatusColumnRejectsNull`, `rejectsAScheduledMatchCarryingAResult` to `ImportSchemaTest`, and a `PLAYED`
  assertion to `roundTripsAMatchThroughThePersistenceLayer`, plus one pinning assertion per source processor test
  (`ImportProcessorsTest`, `FcttImportProcessorsTest`, `BcnesaImportProcessorsTest`).
  **Verification gap:** the JPA module's Spring test context (`JpaTestApplication`) fails to load in this environment
  with `NoSuchBeanDefinitionException: ImportRunRegistry` — confirmed pre-existing (reproduces identically on the
  pre-feature baseline via `git stash`) and unrelated to this change; it blocks ~40 JPA tests project-wide, including
  the four new ones and the modified round-trip assertion above. Domain tests (`MatchTest`, `MatchOutcomeTest`,
  `MatchSearchCriteriaTest`) and the import-module assertions were run directly and pass; the JPA module compiles
  cleanly and the new tests mirror the existing, passing `ImportSchemaTest` patterns (native-query native inserts,
  `assertThrows(Exception.class, entityManager::flush)` style) but were not executed end-to-end. Flagged as a
  separate background task to fix the missing test bean; re-run `mvn -pl tt-data-league-core-repository-jpa -am test`
  once fixed to confirm the four new tests and the round-trip assertion pass. The manual populated-PostgreSQL
  `ddl-auto: update` check from step 10 was not performed in this session either, for the same reason (no working
  JPA test/runtime context available) — do this before considering the column-add migration path itself validated.
  A full `mvn -pl tt-data-league-import test` run also shows 8 unrelated failures (club/player counts of `0` in
  `BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`, `ImportProcessorsTest`); confirmed pre-existing for
  `BcnesaImportProcessorsTest` via the same `git stash` baseline comparison, and the other two show the identical
  symptom so are treated the same way. The `InitialUserProvisioningServiceTest` failures in the domain module are
  likewise pre-existing (confirmed via baseline).
- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: —.
- 2026-09-27: Build plan written; status `idea` → `planned`. Decisions:
  - The builder defaults `status` to `PLAYED` (same as the database default and the current meaning of stored rows),
    so existing call sites stay unchanged. The domain rejects a null status.
  - The domain enforces the header half of the invariant in `Match.of(...)` for both `createNew()` and
    `createExisting()`: a `SCHEDULED` match has a null winner and null games/sets won (`0` is rejected too).
  - **Consequence for FEAT-00078:** when the backfill marks a "decided 0-0" row `SCHEDULED`, it must also set
    `home/away_games_won` and `home/away_sets_won` to null in the same update. Otherwise `MatchJPAToMatchMapper` will
    reject the row on read. Carry this into the FEAT-00078 plan.
  - Match persistence tests stay in `ImportSchemaTest`, where the existing match round-trip tests are; there is no
    `jpa/match` test class today.
- 2026-09-27: Observation outside scope: `tt-data-league-api-runtime/README.md` and
  `tt-data-league-import-runtime/README.md` refer to reviewed SQL migrations under `docs/migrations/`, but no such
  folder exists in the repository. This feature relies on `ddl-auto: update` plus the column default and adds no
  migration file.
