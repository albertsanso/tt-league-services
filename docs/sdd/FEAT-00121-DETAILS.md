# Analysis

## Question

Can the BCNESA and FCTT current-season pipelines (`tt-league-ingest` download + parse, and the Java
`tt-data-league-import` navigators/processors that read their output) become one implementation,
while keeping `source` (`BCNESA` / `FCTT`) as the segregation property and adding two new
properties, `region` and `regionType`?

**Short answer: yes for the Python ingest, mostly yes for the Java current-season import path, no
(not worth it) for the Java legacy-history readers.** Both pipelines scrape the same website with
the same markup and emit the same acta model; they are two independent ports of two legacy
scripts, not two different problems. What really differs is a small, declarable set of
*profile* facts (which index sections, which folder/file naming, which id format), plus the region
each league belongs to.

## Current state

### Same upstream site, same markup

| Aspect | BCNESA (`ingest_bcnesa`) | FCTT (`ingest_fctt`) |
|---|---|---|
| Index page | `https://fctt.cat/competicions-estatals/` | `https://fctt.cat/competicions-estatals/` |
| Index sections used | `Lligues de la representació territorial de <Barcelona\|Girona\|Lleida\|Tarragona>` cards | `TDM` (male) and `Copa Catalana Femenina` (female) headings; `Altres temporades` skipped |
| League page | `/lligues/<league>/?jornada=N` | `/lligues/<group>/?jornada=N` |
| Match markup | `.match-container`, `.team-home/.team-away a[href*=team_name_id]`, `match-results-table`, `codi_jugador` links, acta PDF link | identical |
| Acta model | `docs/acta-model-definition.json`, `federacion = "Federació Catalana de Tennis Taula"` | identical schema and federation |
| Player licence | `codi_jugador` | `codi_jugador` (same namespace) |

The territory is the only real domain difference: BCNESA leagues belong to one of the four
provincial *representacions territorials*; FCTT leagues are Catalonia-wide.

### Accidental differences (two ports of the same thing)

| Concern | BCNESA | FCTT |
|---|---|---|
| HTTP / HTML stack | `requests` + BeautifulSoup | `urllib` + own stdlib `HTMLParser` DOM (`Node`) |
| Size | download 1177 + parse 325 + acta_parser 632 lines | download 1381 + parse 837 lines |
| Retry / pacing | `Fetcher`, `Metrics`, 1.0 s delay, `--delay`, retries 403/404/408/425/429/5xx | `HttpClient`, `Pacer`, `MetricsStore`, 3.0 s delay, `--request_delay`, never retries 401/403, retries 52x |
| User-Agent | browser UAs only | tool UA first, browser UAs on retry |
| Content file | `<season>/<category>/<group>/<phase>/jornada_NN.html` (zero-padded) + `jornada_NN/<id>.pdf` | `.../jornada-<N>.html` + `jornada-<N>/<id>.pdf` |
| Content meta tags | `season`, `category`, `group`, `phase`, `territory`, `match-day`, `source-url`, `acta-content-status/-hash/-issues` | `acta-season`, `acta-category`, `acta-group`, `acta-phase`, `acta-gender`, `acta-territory`, `acta-match-day`, `acta-matches`, per-container `data-acta-content-status` / `data-match-id` |
| Folder values | category = card title (`RTB PREFERENT`), group `G1`, default phase `1a Fase` | category slug (`tdm`, `copa-catalana-femenina`), group slug (`g1`), default phase `regular` |
| JSON folder | category kebab-cased (`rtb-preferent`), group/phase unchanged | all segments slugified |
| JSON file | `jornada_02_local_team_<id>_away_team_<id>.json` | `jornada_2_local_team_<id>_away_team_<id>.json` (+ `_<copy>` for duplicated pairings) |
| `id_partido` | `<season>_<CATEGORYCOMPACT>_<group>_<phasecompact>_<home>-<away>_<day>` | `<season>_<category>_<group>_<phase>_<home>-<away>_<day>` (slugs) |
| `genero` | not emitted | `masculino` / `femenino` |
| `competicion` | category display name from the folder | league page title (cached per group folder) |
| Match status | page-level `complete` / `partial` | per-match `complete` / `partial` / `incomplete`; walkover = complete |
| Incremental parse | rewrite on change; a published acta is never replaced by an unpublished one unless `--force` | `.parse_state.json` with `PARSER_VERSION`, skip unchanged pages, delete JSON a page no longer produces |
| Status scan (`status.py`) | 52 lines | 52 lines, same shape, different globs/meta keys |
| Ingestor (`ingestor.py`) | 92 lines | 91 lines, same shape; FCTT folds `gender` into `--category` |

`ingest_common` already owns run model, scopes, packaging, upload, validation, fingerprint,
health, logs, progress and match-day status; both ingestors are thin wrappers over it. The
duplication is concentrated in `download.py`, `parse.py`/`acta_parser.py` and `status.py`
(~4,400 lines in total, of which an estimated 70–80 % is behaviourally equivalent).

### Java import side

| Area | BCNESA | FCTT | Unifiable? |
|---|---|---|---|
| Ingest layout (2026-2027+) | `BcnesaActasDirectoryNavigator` accepts `jornada_<NN>_local_team_…` | `FcttActasDirectoryNavigator` accepts `jornada_<N>_local_team_…`, maps `tdm` → `tercera-nacional` | **Yes** – same layout once the Python output converges |
| Legacy layout | `acta_<j>_page_<n>.json` / `acta_<n>.json` (2020-2021 … 2025-2026, PDF-derived), `BcnesaMatchdaySplitter`, `BcnesaClubIndex`, Veterans "Other" phases, `BcnesaCompetitionNames` (`rtb-*` → legacy names) | `[male\|female]/<competition>/<group>/jornada-<d>-partido-<m>.json`, `FcttActaOrientation` | **No** – historical readers stay source-specific |
| Team processor | 55 lines, normalises names via `BcnesaTeamNames` | 60 lines, uses `acta.teams()` | Yes (diff is naming normalisation) |
| Player processor | players from game participants | players from lineups + declared doubles + game doubles, `FcttLicenses.isUsable` | Yes (union of both) |
| Match processor | 593 lines, multi-fixture `fixtureIndex`, competition mapping | 622 lines, orientation, `NotStored` early exits | Partly – shared core with two hooks (fixture splitting, orientation) |
| Preview processors | 28 / 37 lines | 28 / 85 lines | Yes |

`ImportSource` (`RFETM`, `BCNESA`, `FCTT`) segregates `federated_club`, `federated_player`,
`player_season`, `team` and `match_record`; `source` is part of fixture identity
(`uk_match_source_fixture_id`) and licence lookups. Keeping it unchanged is required by this
feature and is also what keeps existing rows, checksums and identity guards valid.

The orchestrator (`tt-league-pipeline-orchestrator-core`) already reasons about territories:
`IngestStatusRow.territory`, `ScopeFilter.territory`, `PollUnit.territory`, `UnitKey` hashes
`territory=`, and `SourceVocabulary.Bcnesa` converts `RTT PREFERENT` → `rtt-preferent`. It
duplicates `BcnesaCompetitionNames`.

### Region today

There is no region concept in the platform. It exists implicitly in three places:

1. the BCNESA downloader's `TERRITORIES` and the card headings,
2. the category-folder prefix (`rtb`, `rtg`, `rtl`, `rtt`) that `BcnesaIngestor.scope_matches`
   and the stored competition names (`rtg-preferent`, …) carry,
3. FCTT's hard-coded `territory = "catalunya"`.

`comarcal` is only visible in BCNESA category names (`1a Comarcal`, `2a Comarcal`).

## Findings and risks

1. **Byte-compatibility rule.** `tt-league-ingest/AGENTS.md` makes any output difference against
   the legacy extractors a bug. A unified implementation must therefore first reproduce both
   outputs byte-for-byte through a *profile*, and only change outputs in a separate, explicit,
   versioned step.
2. **`id_partido` is identity.** It feeds `source_fixture_id` and `MatchFixtureIdentityGuard`.
   Each profile must keep its current id format for existing seasons; the formats must never be
   swapped between sources.
3. **Schema change.** `region` / `tipo_region` in the acta JSON means editing
   `docs/acta-model-definition.json` and its copy `ingest_common/schema/acta-model-definition.json`
   together (drift test). Both fields are optional so legacy exports stay valid.
4. **Etiquette differs.** The two downloaders use different delays, UA policies and retryable
   statuses against the *same* host. A unified client must keep each profile's legacy values
   (AGENTS.md: keep legacy delays, retries, User-Agent); harmonising them is a separate decision.
5. **Shared licence namespace.** BCNESA and FCTT players come from the same `codi_jugador`
   namespace and the same clubs, but are stored twice because of `source` scoping. This is a
   strong argument for a later source merge (`FCTT` with `region`) but is **out of scope**: source
   is kept.
6. **Two HTML stacks.** Picking one parser (BeautifulSoup) means the FCTT port must be re-verified
   against its saved-page fixtures; the stdlib `Node` DOM is not kept.
7. **Orchestrator keys.** `UnitKey` hashes `territory=`; changing that string breaks resume of
   in-flight runs. Region must be added *alongside* territory, not instead of it.

## Target model

### `region` and `regionType`

Closed vocabularies, defined once per language and mirrored by tests:

| `region` | Meaning |
|---|---|
| `espana` | Spain-wide (RFETM) |
| `catalunya` | Catalonia-wide (FCTT TDM, Copa Catalana Femenina) |
| `barcelona`, `girona`, `lleida`, `tarragona` | provincial representation (BCNESA) |

| `regionType` | Meaning |
|---|---|
| `nacional` | league of a Spain-wide competition (RFETM) |
| `autonomica` | league of a Catalonia-wide competition (FCTT TDM, Copa Catalana Femenina) |
| `provincial` | provincial representation league (BCNESA, default) |
| `comarcal` | BCNESA league whose category name contains `comarcal` |

Deterministic derivation (no fuzzy matching, no defaults for unknown input):

| Source | Input | `region` | `regionType` |
|---|---|---|---|
| RFETM | any | `espana` | `nacional` |
| FCTT | any league of the `TDM` / `Copa Catalana Femenina` sections | `catalunya` | `autonomica` |
| BCNESA | card heading territory, or category prefix `rtb`/`rtg`/`rtl`/`rtt` | `barcelona`/`girona`/`lleida`/`tarragona` | `comarcal` when the folded category contains `comarcal`, else `provincial` |
| BCNESA legacy (2020-2021…2025-2026, display-name folders) | Barcelona-only export | `barcelona` | same comarcal rule |
| BCNESA | unknown prefix | — fail clearly (issue, file not imported) | — |

Naming: `region` / `tipo_region` in the Spanish acta JSON, `region` / `regionType` in Python
filters, REST, Java domain and API, `region` / `region_type` as DB columns.

### Python: one package, two profiles

New workspace package `tt-league-ingest-fcttweb` (module `ingest_fcttweb`) that owns the
fctt.cat scraper and parser and registers **two** entry points, `bcnesa` and `fctt`, each an
instance of one `FcttWebIngestor` bound to a `SiteProfile`:

```text
SiteProfile
  source: Source                       # BCNESA | FCTT (unchanged segregation)
  sections: SectionMatcher             # territorial cards | TDM + Copa Catalana Femenina
  region_of(league) -> Region          # heading territory / constant catalunya
  region_type_of(league) -> RegionType # comarcal rule / autonomica
  layout: OutputLayout                 # folder values, html/json file names, meta keys
  fixture_id: FixtureIdFormat          # legacy id_partido format per source
  http: HttpPolicy                     # delay, retryable statuses, UA sequence, accept headers
  parse_policy: ParsePolicy            # status model, published-acta guard, stale-file removal
  emits_gender: bool                   # BCNESA false (legacy), FCTT true
```

Shared core (one implementation): league discovery over the index, league schedule/calendar,
match-day fetch + validation + retries + adaptive metrics, minimal page for future days,
`.match-container` extraction, results-table/lineup/doubles/running-score parsing, home/away
orientation, acta assembly, schema validation, incremental write, status scan.

`ingest_bcnesa` and `ingest_fctt` are removed once the golden tests pass; the `Source` values,
entry-point names, CLI `--source bcnesa|fctt`, REST paths, data directories
(`<data>/bcnesa/…`, `<data>/fctt/…`) and ZIP manifests stay unchanged.

### Java: one current-season path, legacy readers kept

- `shared/fcttweb/traverse/FcttWebActasDirectoryNavigator`: walks the ingest layout for a given
  `ImportSource` and yields one context per report, carrying `region` / `regionType` read from the
  payload (or derived by `RegionResolver` when absent).
- `shared/fcttweb/process/*`: one Team, Player, Match and Preview processor set parameterised by
  `ImportSource`, with two hooks: `FixtureSplitter` (BCNESA legacy multi-fixture; identity for the
  ingest layout) and `ActaOrientation` (FCTT legacy; identity when the payload is already home/away).
- `BcnesaActasDirectoryNavigator` and `FcttActasDirectoryNavigator` keep only their legacy layouts
  and delegate ingest-layout files to the shared navigator.

### Persistence and API

- Domain: `Region` and `RegionType` enums in `domain/shared/model`; `Match` gains nullable
  `region` and `regionType` (builder + factories).
- JPA: `match_record.region VARCHAR(20) NULL`, `match_record.region_type VARCHAR(20) NULL`,
  index `idx_match_source_season_region`; schema via `ddl-auto: update` as today; documented in
  `rfetm-datamodel.md`.
- Backfill: an opt-in import-runtime command (report and write modes) that sets the two columns
  on existing rows with `RegionResolver` from `source` + `competition`; never default-enabled.
- REST: match/calendar DTOs expose `region` and `regionType`; calendar/match queries accept
  optional `region` and `regionType` filters (additive, `source` filter unchanged).

# Build Plan

Phases are ordered so that every phase is releasable on its own and the full `mvn test` and
`uv run pytest` stay green after each one.

## Phase 0 – Safety net (no behaviour change)

1. Capture golden fixtures, no network: for BCNESA add saved jornada pages (one per territory,
   one `comarcal` league, one Veterans league, one future day, one partial day, one walkover) under
   `packages/tt-league-ingest-bcnesa/tests/fixtures/saved-pages/`; FCTT reuses
   `packages/tt-league-ingest-fctt/tests/fixtures/saved-pages/` and adds a female league and a
   duplicated pairing. Include the saved index page.
2. Add `tt-league-ingest/tests/golden/` with a harness that runs the *current* `download.main`
   (fake transport serving the fixtures) and `parse.main` for each source into a temp dir and
   stores the produced trees (HTML with meta tags, JSON, metrics shape without timestamps) as
   expected outputs. These goldens are the contract for Phases 1–2.

## Phase 1 – Unified Python package reproducing both outputs byte-for-byte

3. Create `packages/tt-league-ingest-fcttweb/` (`pyproject.toml`, `src/ingest_fcttweb/`) depending
   on `ingest_common`, `requests`, `beautifulsoup4`; add it to the workspace in
   `tt-league-ingest/pyproject.toml`; update `uv.lock`.
4. `ingest_fcttweb/profile.py`: `SiteProfile`, `OutputLayout`, `FixtureIdFormat`, `HttpPolicy`,
   `ParsePolicy` (frozen dataclasses) and the two instances `BCNESA_PROFILE`, `FCTT_PROFILE` holding
   every value in the "Accidental differences" table (delays 1.0 / 3.0 s, retryable status sets, UA
   sequences, file-name patterns, meta keys, default phase, folder normalisation, id format,
   `emits_gender`).
5. `ingest_fcttweb/http.py`: one fetch client (requests session, pacing, `Retry-After`, exponential
   backoff, robots.txt, UA rotation) driven by `HttpPolicy`; reuse `ingest_common.http` where it
   already covers the behaviour.
6. `ingest_fcttweb/metrics.py`: one adaptive metrics store; keep each profile's legacy metrics JSON
   shape and file name.
7. `ingest_fcttweb/discovery.py`: index parsing with a `SectionMatcher` per profile (territorial
   cards with heading territory; TDM / Copa Catalana Femenina with `Altres temporades` skipped) →
   `League(source, region, region_type, territory, gender, category, group, phase, url, title)`.
8. `ingest_fcttweb/markup.py`: BeautifulSoup extraction of `.match-container` into one `Match`
   model (date, time, teams + ids, score, venue, referee, acta URL, rows, players), plus
   validation (`validate_match`, `validate_day`) and `content_status` with the per-profile status
   model.
9. `ingest_fcttweb/download.py`: incremental match-day downloader (complete/future skip, minimal
   page for future days, retries, partial save with issues) writing files through `OutputLayout`.
10. `ingest_fcttweb/acta.py`: acta assembly (header, lineups, doubles, games, running score,
    orientation, final result, walkover) with `FixtureIdFormat` and `emits_gender`.
11. `ingest_fcttweb/parse.py`: incremental parser with `ParsePolicy` (published-acta guard for
    BCNESA; parse-state + stale-file removal for FCTT), schema validation via
    `ingest_common.validation`.
12. `ingest_fcttweb/status.py`: one status scan parameterised by the layout's glob and meta keys.
13. `ingest_fcttweb/ingestor.py`: `FcttWebIngestor(profile)` implementing `download`, `parse`,
    `teams` (raises), `scope_matches` (BCNESA territory-prefix rule; FCTT category-or-gender rule);
    module-level `BcnesaIngestor = …` / `FcttIngestor = …` factories.
14. Switch the entry points: `bcnesa = "ingest_fcttweb.ingestor:BcnesaIngestor"`,
    `fctt = "ingest_fcttweb.ingestor:FcttIngestor"`; keep the CLI `incremental-*` scripts calling
    the same `main` signatures (`--delay` for BCNESA, `--request_delay` for FCTT).
15. Run the Phase 0 goldens against the new package for both profiles until byte-identical; port
    the existing BCNESA/FCTT unit tests (`test_parse`, `test_download*`, `test_discover_leagues`,
    `test_ingestor`, `test_health`, `test_status`, `test_service_logging`) to
    `packages/tt-league-ingest-fcttweb/tests/`, parameterised by profile.
16. Remove `packages/tt-league-ingest-bcnesa` and `packages/tt-league-ingest-fctt`; update
    `tests/test_workspace_layout.py` (federation packages: `ingest_rfetm`, `ingest_fcttweb`), the
    root and ingest `README.md` and `tt-league-ingest/AGENTS.md` dependency-direction line, and
    the Dockerfile if it lists packages.

## Phase 2 – `region` / `regionType` in the ingest output

17. `ingest_common/region.py`: `Region` and `RegionType` enums (values from the Target model) with
    strict `parse`; `region_for(source, territory, category)` implementing the derivation table and
    raising on unknown BCNESA prefixes.
18. Acta schema: add optional `region` (enum of `Region` values) and `tipo_region` (enum of
    `RegionType` values) to `docs/acta-model-definition.json` and copy to
    `ingest_common/schema/acta-model-definition.json`; drift test stays green.
19. Parser: both profiles emit `region` and `tipo_region` after `federacion`; bump FCTT
    `PARSER_VERSION` and add an explicit `--force`-free re-parse for BCNESA (version marker) so
    existing JSON gains the fields once. Regenerate the goldens in a dedicated commit whose only
    diff is the two new keys.
20. Downloader meta: add `acta-region` / `acta-region-type` (FCTT) and `region` / `region-type`
    (BCNESA) meta tags; status rows (`MatchDayEntry`) gain `region` and `region_type`; the REST
    status JSON gains `region` and `regionType` (additive; `territory` unchanged).
21. Filters: `IngestFilters` gains `region` and `region_type`; `territory` stays and is mapped to
    `region` (`Barcelona` → `barcelona`); `scope_matches` honours both; `FILTER_FIELDS`,
    `describe`, `to_dict` and CLI/REST argument parsing updated; tests in `ingest_common`,
    `ingest_cli`, `ingest_rest`.

## Phase 3 – Domain, persistence, import

22. `tt-data-league-core-domain`: `Region`, `RegionType` enums; `Match` + builder gain nullable
    `region`, `regionType`; `RegionResolver` (pure, source + competition folder/name → region
    pair, closed tables, empty result for unknown input); unit tests.
23. `tt-data-league-core-repository-jpa`: `MatchJpaEntity` columns `region`, `region_type`
    (`@Enumerated(STRING)`, nullable), index `idx_match_source_season_region`; mapper both
    directions; `ImportSchemaTest` and a JPA round-trip test; update `docs/rfetm-datamodel.md`
    (enumerations + `match_record`).
24. `tt-data-league-import` `Acta` / `ActaParser`: read optional `region`, `tipo_region`; reject
    unknown values clearly.
25. Shared current-season path: create `shared/fcttweb/traverse/FcttWebActasDirectoryNavigator`
    for the ingest layout (`<season>/<category>/<group>/<phase>/jornada_<N|NN>_local_team_…json`),
    parameterised by `ImportSource`, applying `BcnesaCompetitionNames` for BCNESA and the
    `tdm` → `tercera-nacional` alias for FCTT; make the two existing navigators delegate ingest-layout
    files to it and keep their legacy layouts.
26. Shared processors under `shared/fcttweb/process/`: Team (name normalisation from
    `BcnesaTeamNames` applied for both), Player (union of lineups, declared doubles, game
    participants; licence check from `FcttLicenses`; de-duplicated by licence), Match (common
    resolve/process/preview with `FixtureSplitter` and `ActaOrientation` hooks, `NotStored` early
    exits), Preview validation/classification. Wire them in `NavigatorImportExecutionService`
    (`case BCNESA`, `case FCTT`) and remove the duplicated per-source classes that become empty;
    run `BcnesaImportProcessorsTest`, `MatchLifecycleImportProcessorsTest`,
    `IncrementalActaFixturesTest` and the navigator tests for both sources unchanged.
27. Match processors set `region` / `regionType` from the payload, else from `RegionResolver`;
    both are excluded from `MatchContentChecksum` (no checksum version bump), so existing matches
    are not reported as amended; existing rows get the columns through the step-28 backfill.
28. Backfill: `tt-data-league-import-runtime` option `--backfill-regions[=report|write]`
    (opt-in, source-scoped with `--source`) that resolves and, in write mode, updates the two
    columns for existing `match_record` rows; README updated; tests in report and write mode.

## Phase 4 – API and orchestrator (additive)

29. `tt-data-league-api-rest`: match and calendar DTOs expose `region`, `regionType`; list and
    calendar endpoints accept optional `region` and `regionType` query parameters (validated
    against the enums, 400 on unknown values); repository query methods + JPA tests; OpenAPI/README.
30. `tt-league-pipeline-orchestrator-core`: `IngestStatusRow` reads optional `region` /
    `regionType`; `PollUnit` and `ScopeFilter` keep `territory` (and `UnitKey` unchanged) so run
    resume keeps working; scope building sends `territory` as today. Replace the orchestrator copy
    of `BcnesaCompetitionNames` only if a shared contract is agreed (otherwise keep, note).
31. Frontend exposure of region filters is a follow-up feature (not part of this one).

## Phase 5 – Validation and docs

32. `uv lock --check`, `uv sync --all-packages`, `uv run pytest` in `tt-league-ingest/`; full
    `mvn test` from the root.
33. Live smoke (manual, outside tests): one BCNESA scoped run per territory and one FCTT run with
    `--dry-run` packaging; diff the produced trees against a pre-change run of the same day — only
    the two new keys may differ.
34. Update `tt-league-ingest/README.md`, import-runtime README, `rfetm-datamodel.md`, and
    `deploy/README.md` if package names appear there.

# Implementation Guidelines

- `source` stays the segregation key everywhere: `Source` / `ImportSource` / `PipelineSource`
  values, manifests, data directories, entry-point names, `federated_*` and `player_season`
  scoping, and `source_fixture_id` identity are unchanged. `region` / `regionType` are attributes,
  never part of club, player or fixture identity.
- Byte-compatibility first: Phase 1 must produce identical files for both sources; the only
  intentional output change is the two new keys in Phase 2, in its own commit with regenerated
  goldens.
- Keep each profile's legacy `id_partido` format, file names, meta keys, delays, retries and
  User-Agent policy; harmonising them is out of scope.
- Closed vocabularies only; unknown territory/prefix fails clearly (issue + file skipped), never a
  default region.
- No network in tests; fake transports and saved fixtures only.
- Dependency direction: `ingest_cli` / `ingest_rest` → `ingest_rfetm` / `ingest_fcttweb` →
  `ingest_common`; domain stays Spring/JPA-free; orchestrator only consumes REST.
- Do not change legacy-history Java readers (`BcnesaMatchdaySplitter`, `BcnesaClubIndex`,
  `BcnesaReportFileNames` legacy names, FCTT `male|female` layout) beyond delegating the ingest
  layout.
- Out of scope: merging BCNESA and FCTT into one `source`; merging their clubs/players; frontend
  region filters; changing orchestrator `UnitKey`.

# Notes

- 2026-11-10: Analysis written. Key finding: both pipelines scrape `fctt.cat/competicions-estatals/`
  and the same `.match-container` markup; differences are profile data (sections, layout, id
  format, HTTP etiquette) plus the region. BCNESA and FCTT share the `codi_jugador` licence
  namespace — a later source merge is plausible but explicitly deferred.
- Decision (proposed): Catalonia-wide FCTT leagues use `region = catalunya`,
  `regionType = nacional`. Open question: add a fourth `regionType` (`autonomica`) for
  Copa Catalana Femenina / TDM instead of `nacional`? The enum is closed and defined once, so the
  change is cheap before Phase 2 lands.
- 2026-11-10 decision (resolves the question above): `regionType` gains a fourth value,
  `autonomica`. Catalonia-wide FCTT leagues (TDM, Copa Catalana Femenina) use
  `region = catalunya`, `regionType = autonomica`; `nacional` is reserved for Spain-wide RFETM
  competitions. Target-model tables and acceptance criteria updated accordingly.
- 2026-11-10: status moved to `ready` on user request. The remaining open questions are resolved
  with the plan defaults: `region` / `regionType` stay out of `MatchContentChecksum` (step 27), and
  the unified package is `tt-league-ingest-fcttweb` / `ingest_fcttweb`.
- Open question: include `region` / `regionType` in `MatchContentChecksum` (forces a one-time
  "amended" wave) or keep them out (default in plan: out, columns set by import + backfill).
- Open question: package name `ingest_fcttweb` (scraped site) vs `ingest_catalonia`.
- Risk: unifying on BeautifulSoup drops FCTT's stdlib DOM; Phase 0 goldens must cover FCTT edge
  cases (calendar pages without league title, duplicated pairings, walkovers) before the switch.
- 2026-11-10 execution decision: Phase 1 (one profile-driven `ingest_fcttweb` package, byte-identical to both ports)
  is a ~4,400-line rewrite of two ports that differ in HTTP stack, retry/metrics state machines, CLI arguments and
  HTML parser, and the repository holds no saved BCNESA pages to prove byte equality. On the user's choice
  ("Region first") the region attributes were delivered on top of the existing `ingest_bcnesa` / `ingest_fctt`
  packages and the existing Java processors; the unification is still open. Reordering is safe because the region
  code lives in shared places (`ingest_common.region`, the acta schema, `RegionResolver`, `MatchRegions`) that the
  unified package will reuse unchanged.
- 2026-11-10 execution status by step. **Done:** 17 (`ingest_common/region.py`), 18 (schema and copy), 19 (both parsers
  emit `region` / `tipo_region`; FCTT `PARSER_VERSION` 2; BCNESA needs no marker because its parser rewrites a JSON
  whenever the content differs and the published-acta guard only blocks unpublished replacements), 20 (page meta tags,
  `MatchDayEntry` and status JSON `region` / `regionType`; the status scan derives them from the category, so pages
  downloaded earlier report them too), 21 (`IngestFilters`, scopes, CLI `--region` / `--region-type`, REST
  `region` / `regionType`, `SourceIngestor.scope_error`, BCNESA `--region_type` downloader option), 22, 23, 24, 27, 28
  (domain enums, `RegionResolver`, `Match`, JPA columns + index + datamodel doc, `Acta` / `ActaParser`,
  `MatchRegions` in the three match processors, `--backfill-regions[=report|write]` + README), 29 (REST DTOs,
  `region` / `regionType` query parameters on `/search`, `/calendar` and `/calendar/range`), 30 (`IngestStatusRow`
  reads the optional fields; `UnitKey` / `PollUnit` / `ScopeFilter` untouched). 32 passed: `uv lock --check`,
  `uv sync --all-packages`, `uv run pytest` (398 tests) and the full offline `mvn test` reactor.
  **Not done:** 1-2 (Phase 0 golden fixtures and harness), 3-16 (Phase 1 unified Python package and removal of
  `ingest_bcnesa` / `ingest_fctt`), 25-26 (shared Java navigator and processor set), 33 (live smoke, manual),
  and the `deploy/README.md` / workspace-layout part of 34, which only changes with the package rename.
  31 stays out of scope. The feature therefore stays `in-progress`; the three unmet acceptance criteria are the
  unified package, the golden tests and the shared Java ingest-layout path.
- 2026-11-10 findings while implementing: (a) the stored BCNESA competition names are the legacy display names, and
  only `rtb-*` folders are mapped by `BcnesaCompetitionNames`, so today only Barcelona leagues reach the database;
  `RegionResolver` still resolves `rtg-`, `rtl-` and `rtt-` names so the table does not change when the mapping is
  extended. (b) The `regionType` filter of the BCNESA downloader is an extra script option (`--region_type`), parsing
  is not narrowed by it (as for `territory`), packaging is, through `scope_matches`. (c) The calendar range `facets`
  stay computed before the region filter, like every other filter. (d) A match imported before the feature and
  unchanged keeps `NULL` region until `--backfill-regions` runs.
- 2026-11-10 execution (Phase 0 and Phase 1, steps 1-16, done; the Java steps 25-26 and the manual smoke 33 remain).
  Acceptance criteria 1 and 2 are met.
  **Goldens (steps 1-2):** `tt-league-ingest/tests/golden/` holds saved-page sites (`sites/bcnesa`, `sites/fctt`: one
  league per territory, a comarcal league, a veterans league using real saved markup, a play-off phase, future,
  partial and walkover match days, swapped result tables, a women's cup with unlinked doubles, a final-phase heading,
  a duplicated pairing, group pages without league heading, server faults, PDFs, a published-acta guard case), a
  no-network harness (fake `requests` session, frozen clock, metrics normalised without timings) and 5 scenarios of 3-5
  rounds each. `expected/` was generated by the ORIGINAL `ingest_bcnesa` / `ingest_fctt` code (commit "Phase 0")
  before any new code existed, and re-ran identically (deterministic). The BCNESA saved pages are synthetic but use
  the real site markup (the FCTT saved page is real); no live page was fetched.
  **Package (steps 3-14):** `ingest_fcttweb` = `profile.py` (frozen `SiteProfile`, `HttpPolicy`, `RobotsPolicy`,
  `OutputLayout`, `MetaKeys`, `BCNESA_PROFILE`, `FCTT_PROFILE`), `http.py` (one `FetchClient`), `metrics.py`, `markup.py`
  (BeautifulSoup, both text modes), `discovery.py`, `validated.py` / `calendar_checked.py` (download), `acta.py`,
  `parse.py`, `status.py`, `ingestor.py`; entry points `bcnesa` / `fctt` unchanged.
  The unified output was byte-identical to the goldens on the first complete run, and a mutation (breaking a
  section heading) was caught by them.
  **Finding - what is NOT unified:** the plan assumed 70-80 % equivalent behaviour; the code shows the two ports differ
  in whole algorithms, not only in naming, and a byte-identical result requires keeping both as named models selected
  by profile data: (a) download: BCNESA validates each page alone and REBUILDS the document, FCTT checks it against the
  league calendar and stores the page as served (annotated with meta tags/attributes), with two different metrics JSON
  shapes (one record per request vs per attempt) and retry-learning rules; (b) acta assembly: BCNESA publishes a match
  when its results table has rows (cell classes with positional fallback, orientation by header or running score),
  FCTT decides by per-match status (walkover = complete), needs 3 players per team, keeps placeholder licences for
  unlinked players and recomputes the running score; JSON key order and `id_partido` also differ; (c) parse: BCNESA
  rejects schema-invalid records and never replaces a published JSON, FCTT writes them and counts them invalid, keeps
  a state file and removes stale JSON. Shared once: the fetch client (robots, pacing, `Retry-After`, backoff, agents,
  health counters), discovery, markup helpers, status scan, ingestor, logging, atomic writes. Converging the models
  changes the output and is a separate versioned decision (not part of this feature).
  **Deliberate micro-differences accepted** (only on markup the real site does not produce): one heading-number rule
  (`Jornada N`, else link parameter, else first number), match days from any `data-jornada` plus the headings, pages
  read as UTF-8 else cp1252 for both parsers, one logger style (own handlers, no root logger changes; the BCNESA parse
  no longer calls `logging.basicConfig`).
  **Removed with the legacy packages:** the standalone per-match CLI `ingest_bcnesa.acta_parser.main` (`--input-root`,
  `acta_*.html`, mtime skipping) - nothing in the pipeline, CLI or REST called it; its pure parsing tests were ported
  (`test_acta_table.py`). The FCTT saved-page fixtures moved to `packages/tt-league-ingest-fcttweb/tests/fixtures`.
  **Steps 15-16:** the bcnesa/fctt unit tests are ported (`test_parse_guarded`, `test_parse_stateful`,
  `test_download_validated`, `test_download_calendar`, `test_discovery_territorial`, `test_health_*`,
  `test_ingestor_*`, `test_status_fctt`, `test_service_logging`, `test_acta_table`) plus `test_profile.py`;
  `tests/test_workspace_layout.py`, README, AGENTS.md, the CLI/REST/root `pyproject.toml` files and `uv.lock` follow the
  package change (the Dockerfile lists no package: `uv sync --frozen --package tt-league-ingest-rest` follows the
  lock, so it needed no edit). Validation: `uv lock --check`, `uv sync --all-packages`, `uv run pytest` (407 passed).
  Not run here: `mvn test` (no Java change), live smoke (step 33).
- 2026-11-10 steps 25-26 done (Java shared current-season path, `tt-data-league-import`). New package
  `shared/fcttweb`: `traverse/FcttWebActasDirectoryNavigator` is the one reader of the ingest layout
  (`<season>/<category>/<group>/<phase>/jornada_<N|NN>_local_team_…json`), parameterised by `ImportSource`
  (BCNESA: `BcnesaCompetitionNames`, `G<n>` / Veterans "Other" groups, round fallback from the file name;
  FCTT: `tdm` → `tercera-nacional`, payload `genero`, legacy `male`/`female` folders skipped). It also owns the
  per-report acceptance rules (`handleBcnesaReport`, `handleFcttReport`) that the legacy walks reuse, and a
  `FixtureSplitter` hook (`wholeFile()` for the ingest layout, `BcnesaMatchdaySplitter#asFixtureSplitter` for the
  legacy multi-fixture files). `BcnesaActasDirectoryNavigator` / `FcttActasDirectoryNavigator` keep only their
  legacy walks and unmapped-folder reporting, call the shared navigator for the ingest layout after the legacy
  pass of each season and share one `FcttWebTraversalCounters`. `process/` holds
  `FcttWebTeam|Player|Match|PreviewValidation|PreviewClassificationProcessor` (parameterised by `ImportSource`)
  over `FcttWebFixtureContext`, implemented by `BcnesaMatchReportContext` and `FcttMatchReportContext`; the
  `ActaOrientation` hook has `IDENTITY` and `MIRRORED` (the former `FcttActaOrientation`). Region / regionType
  still come from `MatchRegions` in the shared match processor.
- Why the per-source processors still exist: they are thin subclasses (`@Component`, `@Order`, one bridging
  `process(<Source>MatchReportContext)`) kept because `BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`,
  `MatchLifecycleImportProcessorsTest`, the navigator tests and the runtime `AppTest` build them, the contexts and
  the `List<…MatchReportProcessor>` constructor of `NavigatorImportExecutionService` unchanged, and Spring
  injects the two sources into separate lists by those types. `NavigatorImportExecutionService` therefore needed no
  change: its per-source lists now hold the shared logic. Removed: `BcnesaPreviewClassificationProcessor`,
  `FcttPreviewClassificationProcessor` (the preview service uses `FcttWebPreviewClassificationProcessor`),
  `FcttActaOrientation`, `FcttLicenses` (now `MirroredActaOrientation`, `FcttWebLicenses`).
- Deliberate deviations from the step-26 wording, to keep stored data and identity stable: (a) `BcnesaTeamNames`
  normalisation is applied to BCNESA only, not to FCTT. Applying it to FCTT would make existing stored FCTT team
  names stop matching on re-import and create duplicate teams and matches; revisit only together with a data
  migration. (b) The played header stays source-specific (BCNESA from the fixture's own games, FCTT from
  `resultado_final`, which also covers walkovers without games), as do lineups (BCNESA from game participants, FCTT
  from `alineaciones` with ranking) and the FCTT-only `NotStored` early exits. (c) Lineup and doubles-pair rows
  carry `source` for FCTT only, as before; BCNESA leaves it null (nullable column). Writing it for BCNESA is a
  separate, data-affecting decision. (d) Intentional behaviour changes: the player processor registers the union of
  lineups, declared doubles and game participants for both sources, de-duplicated by licence, and an all-zero
  placeholder licence is no longer registered for BCNESA either (it was already ignored for FCTT); doubles members
  are de-duplicated by player id for both sources. `id_partido` formats, `source_fixture_id` rules and the identity
  guard are untouched.
- Validation: `tt-data-league-import` suite (463 existing tests unchanged, plus the new
  `FcttWebActasDirectoryNavigatorTest` and `FcttWebProcessorsTest`) and the full offline `mvn test` reactor pass.
  Criterion 7 is met by the delivered Java path; criteria 1 and 2 (unified Python package, goldens) stay open, so the
  feature remains `in-progress`.
- 2026-11-10 integration: the region work (commit 2151a85) and the two follow-up branches (Python Phase 0-1, Java steps
  25-26) were merged into `feature/align-bcnesa-fctt`. The "Not done" list of the execution-status note above is
  superseded by the two notes that follow it: only step 33 (the manual live smoke) and the Phase 5 closing checks remain.
