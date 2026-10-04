# Build Plan
Source task: **T18** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.4, 6; gaps G15, G16).

## Acceptance Criteria
- [x] match_record.source_fixture_id is a nullable VARCHAR(100) with a unique (source, source_fixture_id) constraint; external_id is not reused
- [x] Match has a sourceFixtureId field supported by mappers and in-memory repositories
- [x] MatchRepository.findBySourceFixtureId(ImportSource, String) exists
- [x] All three sources fill it from id_partido on create and on upgrade; legacy rows stay null
- [x] rfetm-datamodel.md documents the column and constraint

## Baseline (verified 2026-09-28)

- `Acta.matchId()` (`tt-data-league-import/.../shared/parse/acta/Acta.java`, `@JsonProperty("id_partido")`)
  already parses the id (FEAT-00074). It is `null` for legacy RFETM/BCNESA files and present in every
  2026-2027 file of all three sources and in FCTT 2025-2026. `FcttActaOrientation.toHomeAway` keeps it.
- `Match` (domain) has `externalId` but no fixture id. `MatchJPA` maps `external_id VARCHAR(20)` with
  `uk_match_external_id`; it is too short for `id_partido` (≈ 40–70 chars measured) and must not be
  reused (G16).
- Schema is managed by Hibernate `ddl-auto: update` (runtimes) and `create-drop` (JPA tests); there are
  no migration scripts. A new nullable column and a unique constraint on a nullable column are safe to
  add to a populated PostgreSQL table (existing rows get `NULL`; multiple `NULL`s do not collide).
- The only `MatchRepository` implementors are `MatchRepositoryJpa` and
  `InMemoryRepositories.Matches` (import tests).
- All three processors build headers in their `*LifecycleSource` inner class
  (`buildScheduledMatch`, `buildPlayedMatch`) and hand them to the shared `MatchLifecycleWriter`
  (FEAT-00081). Create uses `saveMatch`; upgrade uses `replaceMatchContent`, which overwrites the whole
  header by id (JPA `matchToMatchJPAMapper` + `save`; in-memory `saved.set`). Reschedule uses
  `updateSchedule`, which touches only schedule columns.
- BCNESA dispatches one `BcnesaMatchReportContext` per split fixture and exposes `fixtureIndex`. Every
  measured file holds exactly one fixture; a multi-fixture file would share one `id_partido`.
- In-memory copies of a `Match` (`InMemoryRepositories.Matches.updateSchedule`, the backfill test
  store's `markScheduled` copy) rebuild the match field by field and would silently drop a new field.

## Contracts

1. **Domain `Match`** (`tt-data-league-core-domain/.../domain/match/model/Match.java`):
   - New `private final String sourceFixtureId`, constructor parameter, `MatchBuilder.sourceFixtureId(String)`,
     and `getSourceFixtureId()`.
   - `public static final int SOURCE_FIXTURE_ID_MAX_LENGTH = 100`.
   - `of(builder)` validation: `null` is allowed (legacy); a blank value or one longer than 100 chars
     throws `IllegalArgumentException` (no silent trimming or truncation). The value is stored verbatim
     and treated as an **opaque** source key (its layout differs by source, analysis 2.5/2.6).
   - `sourceFixtureId` is **not** part of `hasSameNaturalKeyAs` (FEAT-00085 owns the cross-check).
   - `public Match withSourceFixtureId(String sourceFixtureId)`: returns a `createExisting` copy with
     every field equal except the fixture id (no event published). Used by the lifecycle writer and by
     in-memory copies so a new field cannot be dropped again.
2. **Domain port** (`domain/match/repository/MatchRepository.java`):
   `Optional<Match> findBySourceFixtureId(ImportSource source, String sourceFixtureId);` — abstract (no
   default), both arguments required (`NullPointerException` on `null`), always source-scoped. Javadoc:
   exact match on `(source, source_fixture_id)`; returns at most one row by the unique constraint;
   returns matches of any status.
3. **JPA** (`tt-data-league-core-repository-jpa/.../match/`):
   - `MatchJPA`: `@Column(name = "source_fixture_id", nullable = true, length = 100) private String sourceFixtureId;`
     and in `@Table.uniqueConstraints`
     `@UniqueConstraint(name = "uk_match_source_fixture_id", columnNames = {"source", "source_fixture_id"})`.
     The unique constraint's index serves the lookup; no extra `@Index`. `external_id` and
     `uk_match_external_id` are unchanged.
   - `MatchToMatchJPAMapper` / `MatchJPAToMatchMapper`: map the field both ways.
   - `MatchRepositoryHelper`: `Optional<MatchJPA> findBySourceAndSourceFixtureId(Source source, String sourceFixtureId);`
     (derived query).
   - `MatchRepositoryJpa.findBySourceFixtureId`: `Source.valueOf(source.name())`, map with
     `matchJPAToMatchMapper`.
4. **Import filling rule** (per source `*LifecycleSource`):
   - RFETM (`RfetmMatchImportProcessor`) and FCTT (`FcttMatchImportProcessor`): both
     `buildScheduledMatch` and `buildPlayedMatch` call `.sourceFixtureId(acta.matchId())`.
   - BCNESA (`BcnesaMatchImportProcessor`): `.sourceFixtureId(context.fixtureIndex() == 0 ? acta.matchId() : null)`
     in both builders. Only the fixture named by `equipos` owns the file's `id_partido`; an inferred later
     fixture of a multi-fixture file never borrows it (it would violate the unique constraint and
     misidentify the fixture).
   - Legacy files (`id_partido` absent) produce `null`; nothing is derived from file names (G15).
5. **Lifecycle writer** (`shared/match/lifecycle/MatchLifecycleWriter.applyPlayed`, upgrade branch):
   when the rebuilt header's `sourceFixtureId` is `null` and the stored SCHEDULED match has one, rebuild
   the `MatchContent` with `content.match().withSourceFixtureId(stored.getSourceFixtureId())` before
   `replaceMatchContent`, so an upgrade never erases a stored fixture id. A non-null incoming value is
   written as is (a differing non-null value is FEAT-00085's natural-key/`id_partido` cross-check, not
   handled here). Create, reschedule and keep branches are unchanged: reschedule/`UNCHANGED` do not
   backfill a `null` fixture id on an already stored row (see Notes, open question 1).

## Implementation order

1. **Domain model.** Add the field, builder method, getter, length/blank validation and
   `withSourceFixtureId` to `Match`.
2. **Domain port.** Add `findBySourceFixtureId(ImportSource, String)` to `MatchRepository` with javadoc.
3. **Domain tests** (`tt-data-league-core-domain/src/test/java/.../domain/match/model/MatchTest.java`):
   builder round-trips the id; `null` accepted; blank and 101-char values rejected, 100 chars accepted;
   `withSourceFixtureId` preserves every other field, id and status and does not publish
   `MatchCreatedEvent`; `hasSameNaturalKeyAs` ignores the fixture id.
4. **JPA entity and mappers.** Column, unique constraint, both mappers.
5. **JPA repository.** Helper derived query and `MatchRepositoryJpa.findBySourceFixtureId`.
6. **JPA tests** (extend `ImportSchemaTest` or add `match/MatchSourceFixtureIdJpaTest.java` in the same
   style):
   - save and reload a match with a fixture id; `findBySourceFixtureId` finds it, and the same id under
     another `ImportSource` returns empty;
   - two matches of the same source with the same fixture id → `DataIntegrityViolationException` on
     flush; the same fixture id under two sources is allowed; several `null` fixture ids are allowed;
   - `replaceMatchContent` of a SCHEDULED match writes the new header's fixture id; `updateSchedule`
     leaves it untouched;
   - a 100-char value persists (column length).
7. **In-memory repository** (`tt-data-league-import/src/test/java/.../process/InMemoryRepositories.java`):
   `findBySourceFixtureId`; `saveMatch` throws `IllegalStateException` on a duplicate non-null
   `(source, sourceFixtureId)` of another match id (mirrors the unique constraint); `updateSchedule` and
   the backfill `markScheduled` copy carry `sourceFixtureId` (use `withSourceFixtureId` or copy the
   field). Cover this in `InMemoryMatchRepositoryTest`.
8. **Import processors.** Apply contract 4 to the three `*LifecycleSource` classes.
9. **Lifecycle writer.** Apply contract 5; add cases to `MatchLifecycleWriterTest`: upgrade with
   incoming `null` keeps the stored id; upgrade with incoming id overwrites a stored `null`.
10. **Import tests** (`MatchLifecycleImportProcessorsTest`, plus the per-source processor tests where the
    fixtures live):
    - RFETM 2026-2027 unpublished acta → SCHEDULED match with `sourceFixtureId` equal to its `id_partido`;
      then the published acta of the same fixture → upgraded match keeps the same id and fixture id.
    - FCTT unpublished (`jornada-<d>-partido-<home>-<away>.json`) then published
      (`jornada-<d>-partido-<n>.json`) versions → one match, same fixture id (G15; reuse the fixtures of
      `IncrementalActaFixturesTest`).
    - BCNESA 2026-2027 pending acta → SCHEDULED match with fixture id; a legacy BCNESA acta and a legacy
      RFETM acta → `sourceFixtureId` `null`.
    - `findBySourceFixtureId(source, id_partido)` returns the stored match for each source.
11. **Documentation.** `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`, `match_record`:
    add the `source_fixture_id | VARCHAR(100) | Yes | uk_match_source_fixture_id` row, a paragraph
    (opaque source key from `id_partido`, null for legacy rows, never derived from file names, not
    `external_id`, BCNESA first-fixture rule, `ddl-auto: update` adds it as nullable), and
    `uk_match_source_fixture_id` on `(source, source_fixture_id)` to the unique-constraint list. No README
    change: CLI, configuration and launch behavior are unchanged.
12. **Validation.** `mvn -pl tt-data-league-import -am test`, then the full `mvn test` from the root.

# Implementation Guidelines

- Affected modules: domain, JPA, import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Do not reuse, widen, or repopulate `match_record.external_id` / `Match.externalId`; the fixture id is a
  separate column with its own source-scoped constraint.
- Treat `id_partido` as opaque: never parse its segments, never derive it from file names or payload
  fields, never trim, pad or truncate it. Invalid values (blank, > 100 chars) fail the fixture through
  the existing per-processor failure handling instead of being normalised.
- Every lookup by fixture id is scoped by `ImportSource`; there is no unscoped `findBySourceFixtureId(String)`.
- Out of scope: the natural-key vs `id_partido` cross-check and duplicate prevention (FEAT-00085),
  snapshot reconciliation by fixture id (FEAT-00086), duplicate `id_partido` detection within an upload
  (FEAT-00087), exposing the fixture id in REST/MCP/GraphQL DTOs, and any backfill of legacy rows. The
  write path's natural-key lookup stays the primary identity; this feature only stores and queries the id.
- A stored-vs-incoming mismatch of two non-null fixture ids on upgrade is written as incoming here;
  FEAT-00085 turns it into a reported issue.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 2: incremental import. Depends on: FEAT-00077 (T2), FEAT-00081 (T6).
- 2026-09-28: Build plan written against the code after FEAT-00082 (commit 5c30462); status `idea` →
  `planned`. Decisions:
  - Filling happens in each source's `*LifecycleSource` header builders, so create and upgrade share one
    rule; `MatchLifecycleWriter` only protects a stored id from being erased by a `null` on upgrade.
  - BCNESA assigns the file's `id_partido` only to fixture index 0 (the fixture named by `equipos`);
    all measured files hold one fixture, so this only guards the multi-fixture split path.
  - Added `Match.withSourceFixtureId` so the writer and in-memory copies do not rebuild the match field by
    field (the in-memory `updateSchedule` copy would otherwise drop the new field).
  - The in-memory repository mirrors the `(source, source_fixture_id)` unique constraint so import tests
    fail the same way JPA would.
- Open question 1 (recommendation: follow-up, not this feature): SCHEDULED rows already stored before
  this feature ships (e.g. 2026-2027 imports under FEAT-00081) keep `source_fixture_id = NULL` until they
  are upgraded, because reschedule/unchanged do not write the header. If FEAT-00085/00086 need those ids
  earlier, add an explicit `assignSourceFixtureId(UUID, String)` port guarded by `source_fixture_id IS NULL`
  rather than widening `updateSchedule`. Re-importing the 2026-2027 snapshots into a clean database avoids it.
- 2026-09-28: Implemented per plan steps 1–12; status `in-progress` → `in-review`. Validation:
  - Domain `MatchTest` (5 new cases), JPA `MatchSourceFixtureIdJpaTest` (7 cases incl. unique-constraint
    violation, cross-source reuse, multiple nulls, 100-char persistence, replace/updateSchedule behavior),
    `InMemoryMatchRepositoryTest` (3 new cases), `MatchLifecycleWriterTest` (2 upgrade fixture-id cases),
    `MatchLifecycleImportProcessorsTest` (RFETM create+upgrade, RFETM legacy null, FCTT shared id_partido,
    BCNESA index 0/index 1, BCNESA legacy null) all pass.
  - Full reactor `mvn test`: domain, JPA (78), import-runtime, api-rest, api-mcp, api-runtime modules
    green for this feature's tests. Remaining failures are PRE-EXISTING on base commit 5c30462 (verified
    in a clean HEAD worktree) and unrelated to FEAT-00083: 4 `InitialUserProvisioningServiceTest` cases
    (SEED_USERS grew to 3), 7 club-registration cases in `ImportProcessorsTest`/`BcnesaImportProcessorsTest`/
    `FcttImportProcessorsTest`, and 1 `TeamToClubConsolidationProcessorTest` case. No README change needed
    (CLI/configuration/launch unchanged).
- 2026-09-28: On user request the pre-existing failures were fixed in this change too, and the full
  reactor `mvn test` now passes (10/10 modules, 0 failures):
  - `InitialUserProvisioningService` now sets `UserRole.ADMIN` on every seeded account (real bug: the
    class javadoc and tests promised ADMIN but `User.createNew` yields PRACTITIONER); its test was
    updated to the three seed users (albert/oscar/test) added in b54fab9.
  - `ImportProcessorsTest`, `BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`: stale
    `FederatedClub`/`Club`/`FederatedPlayer`/`Player` assertions replaced with the current acta-import
    contract (the processors register `Team` and `PlayerSeason` only; club/player rows belong to the
    consolidation processors). Unused in-memory club/player stores removed.
  - `TeamToClubConsolidationProcessorTest.groupsNormalizedSpellingsAndVerifiedAbbreviations`: asserts
    one created `FederatedClub` row (`clubs.size()`) instead of a canonical `Club`, which is owned by
    `FederatedClubToCanonicalClubConsolidationProcessor`.
- 2026-09-28: Closed on explicit user request after full `mvn test` passed with 0 failures across all
  10 modules; all five acceptance criteria verified above. Status `in-review` → `done`.
