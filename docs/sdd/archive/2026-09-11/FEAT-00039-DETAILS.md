# Build Plan
> Scope note: per the registry's Description #3, this feature does not touch the import process or
> data sources. `phase` is added as a first-class, settable, persisted, searchable, and displayable
> property end to end from the domain model down to the UI, but no importer (`BcnesaMatchImportProcessor`,
> `RfetmMatchImportProcessor`, `FcttMatchImportProcessor`) is changed to populate it. Every existing and
> newly imported match therefore keeps `phase = null` until a separate import feature wires a source for
> it; "sensible default/handling" (AC2) means treating `null` as a normal, displayable "not set" value,
> not inventing a placeholder value.

1. **Domain model** — `tt-data-league-core-domain/.../match/model/Match.java`
   - Add a nullable `String phase` field, constructor param, `MatchBuilder.phase(String)`, and
     `getPhase()`, following the existing pattern used for `city`/`venue`/`refereeName`.

2. **Persistence** — `tt-data-league-core-repository-jpa/.../match/model/MatchJPA.java`
   - Add `@Column(name = "phase", nullable = true, length = 255) private String phase;`. No manual
     migration needed: both runtimes use Hibernate `ddl-auto: update`
     (`tt-data-league-import-runtime/src/main/resources/application.yml`,
     `tt-data-league-api-runtime/src/main/resources/application.yml`), which adds the column on next
     boot; existing rows get `NULL`. Do not touch `uk_competition_season_group_round_teams` or any other
     constraint — out of scope (import-side natural-key concerns belong to the separate import feature).
   - `MatchToMatchJPAMapper` / `MatchJPAToMatchMapper`: set/get `phase` alongside the other scalar
     fields.

3. **Match detail (REST)**
   - `MatchDetailReadModel`: add `String phase` field.
   - `FindMatchDetailsQueryHandler.compose`: pass `match.getPhase()` into the read model.
   - `MatchDetailDto`: add `String phase` field and thread it through `from(...)`.

4. **Match search (REST) — filter and display**
   - `MatchSearchCriteria`: add an optional `String phase` component (nullable, trimmed to `null` when
     blank — same normalization already applied to `playerName`); no mandatory-field validation, matching
     how `fromDate`/`toDate`/`playerName` are optional today.
   - `MatchSearchReadModel` (`tt-data-league-core-domain/.../match/find/dto/`): add `String phase`.
   - `SearchMatchesQueryHandler.toReadModel`: pass `match.getPhase()` through.
   - `MatchRepository.searchMatches` / `countMatches` callers already take the whole `MatchSearchCriteria`,
     so no port signature change beyond the new record component.
   - `MatchRepositoryHelper.search` / `countSearch` JPQL: add `and (:phase is null or m.phase = :phase)`
     and a `@Param("phase") String phase` parameter to both queries; `MatchRepositoryJpa.searchMatches` /
     `countMatches` pass `criteria.phase()` through.
   - `MatchDto` (`tt-data-league-api-rest/.../match/MatchDto.java`): add `String phase` field, threaded
     through `from(MatchSearchReadModel)`.
   - `MatchController.search`: add `@RequestParam(name = "phase", required = false) String phase` and
     pass it into `MatchSearchCriteria`.

5. **Frontend**
   - `src/api/matches.js`: `searchMatches` — add `phase` to the `optional` filter list sent as a query
     param, same as `playerName`.
   - `src/pages/MatchesSearchPage.jsx`: add a `phase` entry to the `filters` memo (read from/written to
     the `phase` URL search param, same pattern as `playerName`), a text input filter field in the filter
     grid, and a `phase` line in each result item next to competition/score.
   - `src/pages/MatchDetailPage.jsx`: add a `phase` row in the `<dl>` next to round/group, falling back to
     `t('common.unavailable')` when `null` (same pattern as season/winner).
   - `src/i18n/en.js`, `es.js`, `ca.js`: add `matchesPage.phase` (label used by both the detail row and the
     filter field), written in each locale's own language.

6. **Tests**
   - Domain: extend (or add) a `Match`/`MatchBuilder` round-trip test to cover `phase`.
   - `MatchSearchCriteriaTest`: assert `phase` defaults to absent when not supplied and is normalized
     (blank → `null`) when supplied, mirroring the existing `playerName` case.
   - `MatchRepositoryJpa`/`MatchRepositoryHelper` level test (or extend `ImportSchemaTest` if that is
     where match persistence round-trips are covered today): assert a match's `phase` persists and reloads
     correctly, and that `searchMatches`/`countMatches` correctly narrow results by `phase` while leaving
     it unfiltered when omitted.
   - `SearchMatchesQueryHandlerTest`/`FindMatchDetailsQueryHandlerTest` (if such tests exist) or the REST
     controller test layer: assert `phase` appears in both the detail and search DTOs.
   - Frontend: if there is existing test coverage for `MatchesSearchPage`/`MatchDetailPage`
     (`*.test.jsx`), extend it for the new filter field/display row; otherwise a manual UI check is
     acceptable given this codebase's current frontend test coverage level — verify against `AGENTS.md`
     guidance for `tt-data-league-frontend` before deciding.

# Implementation Guidelines

- `phase` is a plain, unnormalized string (no enum/lookup table) — the eventual import sources (e.g.
  BCNESA folder names like `1a Fase`) have no fixed vocabulary across competitions/seasons, and this
  feature does not define one.
- Do not modify any importer (`BcnesaMatchImportProcessor`, `RfetmMatchImportProcessor`,
  `FcttMatchImportProcessor`), `BcnesaMatchReportContext`/navigator phase parsing, or the match
  natural-key/unique constraint — all out of scope per the registry Description.
- Do not backfill `phase` for existing rows; they stay `NULL` until a future import feature sets it.
- Search filtering on `phase` is an exact match against the stored value (trimmed, case-sensitive, same
  as `competition`) — no partial/contains matching, since it is intended to filter by a specific known
  phase, not fuzzy search.

# Notes
- Registry acceptance criteria (added after the feature was created) explicitly requires both viewing
  and filtering matches by `phase` in the UI, and explicitly excludes import-process changes. This build
  plan follows that scope: full domain/persistence/REST/UI wiring for a settable, filterable `phase`
  property, but no source actually populates it yet.
- `BcnesaActasDirectoryNavigator` already parses a `phase` path segment into `BcnesaMatchReportContext`
  but it is currently discarded by `BcnesaMatchImportProcessor`. Wiring that into `Match.phase` (and the
  related natural-key correctness question — BCNESA round numbers can repeat across phases within the
  same group) is deferred to the separate import feature the registry Description refers to.

## Implementation validation (in-progress → in-review)
- Implemented all six build-plan steps as written: `Match`/`MatchJPA`/mappers, `MatchDetailReadModel`/
  `MatchDetailDto`, `MatchSearchCriteria`/`MatchSearchReadModel`/`MatchDto`/`MatchRepositoryHelper`
  JPQL/`MatchController`, and the frontend (`MatchDetailPage.jsx`, `MatchesSearchPage.jsx`,
  `matches.js`, `en.js`/`es.js`/`ca.js`).
- No importer, natural-key, or unique-constraint code was touched, per scope.
- Verified: `mvn compile` on `tt-data-league-core-domain`, `tt-data-league-core-repository-jpa`,
  `tt-data-league-api-rest`, and `tt-data-league-import` all succeed with the new `phase` field/params.
- Verified: `MatchSearchCriteriaTest` (new phase-normalization cases) and the full core-domain test suite
  pass. Added `ImportSchemaTest` cases (`phase` persistence round-trip, `phase = null` default, and
  `searchMatches`/`countMatches` phase filtering) are written and compile, but could not be executed:
  every `@SpringBootTest` class in `tt-data-league-core-repository-jpa` (`JpaTestApplication`) fails to
  load its context in this environment with `NoSuchBeanDefinitionException: ... ImportRunRegistry`, and
  this reproduces identically on unmodified `main` (verified via `git stash`) — a pre-existing,
  environment-specific issue unrelated to this feature. `FederatedPlayerRepositoryJpaTest` is the only
  JPA test class that currently passes.
- Verified: `npx eslint` on all changed frontend files is clean, and the full frontend suite
  (`npx vitest run`, 153 tests) passes. No new frontend tests were added, consistent with
  `tt-data-league-frontend/AGENTS.md` ("does not define component tests; ... introduce focused tests
  only as part of [substantial stateful/API work]") — this change mirrors the existing `playerName`
  filter pattern exactly.
- Follow-up for whoever picks up `ImportSchemaTest` next: once the `ImportRunRegistry` wiring is fixed,
  re-run `mvn -pl tt-data-league-core-repository-jpa -am test -Dtest=ImportSchemaTest` to confirm the new
  `phase` cases actually pass (they are compiled and reviewed, but unexecuted).
- Implementation complete per build plan; ImportSchemaTest additions written but unexecuted due to a pre-existing environment issue (unrelated to this feature).
