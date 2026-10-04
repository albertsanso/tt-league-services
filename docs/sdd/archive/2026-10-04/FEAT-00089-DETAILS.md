# Build Plan
Source task: **T13** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.2 diagram (correction); section 4.3 "PLAYED → skip (T13 later)"; open question 3).

## Acceptance Criteria
- [x] match_record.source_checksum stores a checksum of the applied acta and is documented in rfetm-datamodel.md
- [x] When enabled and a PLAYED acta's checksum changes, the match is re-applied via replaceMatchContent and an audit line is logged
- [x] The behaviour is opt-in and disabled by default
- [x] Report mode performs the same detection without persistence writes

## Baseline (verified 2026-09-29, after FEAT-00088 / commit 281ad9a)

- All three match processors (`RfetmMatchImportProcessor`, `FcttMatchImportProcessor`,
  `BcnesaMatchImportProcessor`) store fixtures through `MatchLifecycleWriter.apply(classification,
  existing, source)`, which plans with the pure `MatchLifecyclePlanner` and executes the
  `MatchLifecycleAction`. A PLAYED acta on a stored PLAYED match plans `NONE` / `PLAYED_KEPT`: an
  amended acta is silently ignored today (analysis 4.3).
- `MatchLifecycleSource.buildPlayedContent(id, existing)` is read-only in every source (lookups only:
  `PlayerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason`, teams already resolved), so it
  can be called to compute a checksum without side effects. The planner deliberately never calls it.
- `MatchRepository.replaceMatchContent(MatchContent)` (FEAT-00080) already accepts a PLAYED content
  for any existing match id (SCHEDULED or PLAYED), keeps the id, refuses a natural-key change, and
  is one transaction (class-level `@Transactional` in `MatchRepositoryJpa`; rollback tests exist).
  The writer's `keepStoredFixtureId` already protects the stored `source_fixture_id` on upgrade.
- `Match` (domain) has no checksum; `MatchJPA` / `match_record` has no `source_checksum` column.
  The schema evolves by `ddl-auto: update` (api-runtime and import-runtime `application.yml`); there
  is no Flyway/Liquibase. `MatchRepository` implementors: `MatchRepositoryJpa`,
  `InMemoryRepositories.Matches`, and test stubs/decorators in `SnapshotReconcilerTest`,
  `IncrementalPreviewServiceTest` and `NavigatorImportExecutionLifecycleTest`.
- Opt-in behaviour flows through `ImportExecutionOptions(clubConsolidationMode,
  playerConsolidationMode, rfetmTeamsFolder, batchSize)`: import runtime builds it from CLI flags in
  `App.run` (`ImportRuntimeArguments` / `ImportRuntimeCliContract`, `--flag[=write|report]` pattern
  via `ModeSelection`), the api runtime from `ImportExecutionProperties`
  (`tt.league.import.execution.*`, `null` mode = disabled) and
  `NavigatorBackedImportResourceProcessService.effectiveOptions`. `NavigatorImportExecutionService`
  creates the per-run `ImportRunContext(source, season)` that processors reach via
  `context.runContext()`.
- Outcomes are counted in `ImportRunContext.recordMatchOutcome`; reportable outcomes become warning
  issues that never change the run status (`ImportRunStatusPolicy`). `ImportLifecycleCounters`
  maps only five outcomes plus unresolved fixtures.

## Contracts

1. **Checksum value** (`shared/match/lifecycle/MatchContentChecksum`, import module, final utility):
   `static String of(MatchContent content)` → `"v1:" + lowercase hex SHA-256` (67 chars) of a canonical
   UTF-8 text of what the import applies, independent of generated UUIDs and list order:
   - header: source, sourceFixtureId, externalId, competition, season, groupNumber, round, phase,
     dateTime (ISO instant), city, venue, home/away/winner team ids, refereeName, refereeLicense,
     home/away games won, home/away sets won, protested, status. Never the match id.
   - lineups sorted by (team id, letter, position): team id, letter, position, player-season id, ranking.
   - games sorted by gameNumber: every field except id and match; players by player-season id.
   - set scores sorted by (gameNumber of their game, setNumber); doubles pairs sorted by
     (gameNumber, side, player-season id).
   - each field is length-prefixed and `null` has its own token, so adjacent values cannot collide.
   Decision: a *content* checksum, not a raw-file checksum - BCNESA splits one matchday file into
   several fixtures, file formatting/renames must not trigger rewrites (gap G15), and it detects
   exactly the changes a re-apply would write. The `v1:` prefix versions the canonical form: a stored
   value with another prefix is treated like a missing baseline (contract 4), never as an amendment.
2. **Domain `Match`**: new nullable `String sourceChecksum` field, builder property, getter and
   `withSourceChecksum(String)` (copy, like `withSourceFixtureId`). Builder invariant (next to the
   FEAT-00077 one): `SCHEDULED` ⇒ `sourceChecksum == null`. Not part of the natural key or equality.
3. **Domain port** `MatchRepository.recordSourceChecksum(UUID matchId, String sourceChecksum)`:
   abstract; both arguments required (`NullPointerException`); updates only the checksum of a stored
   PLAYED match; unknown id → `IllegalStateException`; SCHEDULED match → `IllegalStateException`
   (mirrors `updateSchedule`'s failure style). JPA: `@Modifying` JPQL on `MatchRepositoryHelper`
   guarded by `status = PLAYED`, then `existsById` to pick the error. In-memory: replace the stored
   match with `withSourceChecksum`.
4. **Planner** (`MatchLifecyclePlanner`, stays pure and repository-free):
   - `MatchLifecycleAction` gains `REAPPLY_PLAYED` (replace a stored PLAYED match's content, keeping
     its id) and `RECORD_SOURCE_CHECKSUM` (write only the checksum of a stored PLAYED match).
   - `MatchLifecycleOutcome` gains `PLAYED_AMENDED` (re-applied; reportable) and
     `PLAYED_AMENDMENT_REPORTED` (detected in report mode, nothing written; reportable).
   - new `MatchLifecyclePlan planAmendment(Match stored, String incomingChecksum,
     AmendedActaMode mode)` for a stored PLAYED match and a PLAYED acta:
     stored checksum equal → `NONE`/`PLAYED_KEPT`; stored checksum null or not `v1:` →
     `RECORD_SOURCE_CHECKSUM`/`PLAYED_KEPT` in WRITE, `NONE`/`PLAYED_KEPT` in REPORT (baseline
     adoption, never an amendment - otherwise enabling it would rewrite every legacy match);
     different → `REAPPLY_PLAYED`/`PLAYED_AMENDED` in WRITE, `NONE`/`PLAYED_AMENDMENT_REPORTED` in REPORT.
   - `plan(...)` and `planCreation(...)` are unchanged; the existing decision table stays the default.
5. **Mode** `enum AmendedActaMode { WRITE, REPORT }` (`shared/match/lifecycle`); `null` means disabled,
   exactly like `ConsolidationMode` in `ImportExecutionOptions`.
6. **Writer** (`MatchLifecycleWriter`):
   - `CREATE_PLAYED` and `UPGRADE_TO_PLAYED` always store `MatchContentChecksum.of(content)` on the
     header (`withSourceChecksum`), whatever the mode, so a later opt-in has a baseline for every
     match imported after this feature. Checksum is computed on the content actually written (after
     `keepStoredFixtureId`).
   - new overload `apply(ActaClassification, Optional<Match>, MatchLifecycleSource, AmendedActaMode mode,
     Path location)`; the 3-arg `apply` delegates with `mode = null`, `location = null` (current
     behaviour, byte-for-byte). When `mode != null` and the plan is `NONE`/`PLAYED_KEPT` for a stored
     PLAYED match, the writer builds `source.buildPlayedContent(stored.getId(), true)`, applies
     `keepStoredFixtureId`, computes the checksum and executes `planner.planAmendment(...)`:
     `REAPPLY_PLAYED` → `replaceMatchContent(content.withSourceChecksum)`; `RECORD_SOURCE_CHECKSUM` →
     `recordSourceChecksum`.
   - **Audit line**: for `PLAYED_AMENDED` and `PLAYED_AMENDMENT_REPORTED` the writer logs one INFO line
     on a dedicated logger `org.cttelsamicsterrassa.data.load.audit.AmendedActa`:
     `amended-acta mode=<WRITE|REPORT> source=<src> season=<s> competition=<c> group=<g> round=<r>
     phase=<p> fixture=<source_fixture_id|-> match=<uuid> location=<path> checksum=<old|-> -> <new>`.
     Baseline adoption logs at DEBUG only.
7. **Options and run context**:
   - `ImportExecutionOptions` gains a trailing `AmendedActaMode amendedActaMode` (nullable = disabled);
     `defaults()` passes `null`. Update every constructor call (import-runtime `App`, api-runtime
     `ImportExecutionProperties.toOptions`, `NavigatorBackedImportResourceProcessService.effectiveOptions`,
     `NavigatorImportExecutionLifecycleTest`).
   - `ImportRunContext` gains `ImportRunContext(source, season, AmendedActaMode)` and
     `amendedActaMode()`; the 2-arg constructor keeps `null`. `NavigatorImportExecutionService.execute`
     passes the option. The preview service and the processors' fallback contexts keep the 2-arg form.
   - Processors call the 5-arg `apply` with `context.runContext().amendedActaMode()` and the report
     file path; their existing `recordOutcome` turns the new reportable outcomes into warnings with the
     reason `"amended acta re-applied"` / `"amended acta detected (report mode)"`.
8. **Runtime switches** (all default disabled):
   - import runtime: `--detect-amended-actas[=write|report]` (`ImportRuntimeCliContract`,
     `ImportRuntimeArguments` via the existing `ModeSelection` + a `toAmendedActaMode` mapper; bare flag
     = write; any other value fails clearly like the backfill flag).
   - api runtime: `tt.league.import.execution.amended-acta-detection` =
     `${IMPORT_EXECUTION_AMENDED_ACTA_DETECTION:disabled}` in `application.yml`; `disabled`/`none`/blank
     → `null`, otherwise `AmendedActaMode.valueOf` (invalid value fails at startup).
9. **JPA**: `MatchJPA.sourceChecksum` → `@Column(name = "source_checksum", nullable = true, length = 80)`,
   both mappers, `recordSourceChecksum` helper query. `markScheduledByIds` (backfill) also sets
   `sourceChecksum = null`, keeping the `SCHEDULED` ⇒ null invariant. No index or constraint.
10. **Preview** (FEAT-00088): unchanged behaviour - it plans with `plan(...)` only and therefore still
    reports `PLAYED_KEPT` for stored PLAYED matches. `PreviewChange.of` maps the new actions/outcomes
    to an `IllegalStateException` ("not planned by preview") so the exhaustive switches compile and a
    future misuse fails loudly.

## Steps

1. **Checksum** (contract 1). Add `MatchContentChecksum` + `MatchContentChecksumTest`: same content with
   different UUIDs and shuffled child lists → same value; each header field, a lineup player, a game
   score, a set score and a doubles player change → different value; `null` vs `""` differ; prefix `v1:`.
2. **Domain** (contracts 2-3). `Match.sourceChecksum` + builder + `withSourceChecksum` + SCHEDULED
   invariant; `MatchRepository.recordSourceChecksum`. Domain tests for the invariant and the copy.
3. **JPA** (contract 9). Column, mappers, `recordSourceChecksum`, backfill null-out. Tests: round-trip
   of `source_checksum` through save/find and `replaceMatchContent`; `recordSourceChecksum` on PLAYED,
   unknown id and SCHEDULED; backfill clears it (extend the existing backfill JPA test).
4. **In-memory and stubs**. `InMemoryRepositories.Matches.recordSourceChecksum` (+ test in
   `InMemoryMatchRepositoryTest`); the `SnapshotReconcilerTest` stub delegates; the
   `IncrementalPreviewServiceTest` write-throwing decorator throws; `NavigatorImportExecutionLifecycleTest`
   stub implements it.
5. **Planner** (contracts 4-5). Add `AmendedActaMode`, the two actions, the two outcomes (both
   `isReportable()`), `planAmendment`. Extend `MatchLifecyclePlannerTest`: equal / null / foreign-prefix /
   different checksum × WRITE / REPORT; argument validation (stored must be PLAYED). Update
   `PreviewChange.of` (contract 10) + `PreviewChangeTest`.
6. **Writer** (contract 6). Checksum on create/upgrade; 5-arg `apply`; audit logger. Extend
   `MatchLifecycleWriterTest`: create/upgrade store the checksum; disabled mode never calls
   `buildPlayedContent` for a stored PLAYED match and writes nothing; WRITE re-applies via
   `replaceMatchContent` keeping id and stored fixture id; REPORT writes nothing; baseline adoption
   writes only the checksum; audit line captured (Logback `ListAppender` on the audit logger, as
   already available through spring-boot-starter-test / logback in the module - verify, otherwise assert
   via the returned outcome only and note it).
7. **Options/context/processors** (contract 7). Extend options, run context and the execution service;
   switch the three processors to the 5-arg `apply`.
8. **Runtimes** (contract 8). CLI flag + parsing tests in the existing `ImportRuntimeArguments` test;
   api-runtime property + `ImportExecutionProperties` test (disabled default, `write`, `report`, invalid).
9. **End-to-end tests** (import module, in-memory repositories), `AmendedActaImportTest`, one FCTT and
   one RFETM played acta (BCNESA covered by the writer tests unless a fixture is cheap):
   - import A → stored PLAYED with checksum; re-import A with detection WRITE → `PLAYED_KEPT`, no writes.
   - amended A' (changed game score and final result) with detection disabled (default) → `PLAYED_KEPT`,
     stored content unchanged.
   - A' with WRITE → `PLAYED_AMENDED`, same match id, new games/result, new checksum, one warning issue,
     round progress unchanged; running A' again → `PLAYED_KEPT` (idempotent).
   - A' with REPORT → `PLAYED_AMENDMENT_REPORTED`, repository unchanged (write-throwing decorator).
   - legacy PLAYED match with null checksum + WRITE → checksum recorded, content untouched, no warning.
   - pending/partial acta for a stored PLAYED match is still a regression in every mode.
10. **Documentation.** `rfetm-datamodel.md`: `source_checksum` row in the `match_record` table and a
    **FEAT-00089 source checksum** paragraph (content checksum, `v1:` versioning, null for SCHEDULED and
    legacy rows, set on create/upgrade/re-apply, cleared by the backfill, baseline adoption, no FK/index).
    `tt-data-league-import-runtime/README.md`: CLI table row + an "Amended actas" subsection (modes,
    default disabled, audit logger name, warnings). `tt-data-league-api-runtime/README.md`: the
    `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION` setting.
11. **Validate.** `mvn -pl tt-data-league-import -am test`, then `mvn test` from the root; review the
    diff (no `target/`, no secrets, no unrelated POM changes).

# Implementation Guidelines

- Affected modules: core-domain (`Match`, `MatchRepository`), core-repository-jpa (`MatchJPA`, mappers,
  helper, `MatchRepositoryJpa`, backfill), import (checksum, planner, writer, options, run context,
  processors, preview mapping, in-memory repos and stubs), import-runtime (CLI), api-runtime
  (properties, `application.yml`), plus the three docs.
- Opt-in and disabled by default in every entry point (CLI, api properties, `ImportExecutionOptions.defaults()`,
  2-arg `ImportRunContext`). With detection disabled the only behaviour change is that newly created or
  upgraded PLAYED matches carry a checksum.
- Report mode runs the same detection (including `buildPlayedContent` and the checksum) and performs no
  persistence write, including no baseline adoption.
- One decision table: the amendment rule lives in `MatchLifecyclePlanner.planAmendment`, and the re-apply
  goes through the existing `replaceMatchContent` - no new delete/insert path.
- A re-apply never changes the match id, the natural key (enforced by `replaceMatchContent`) or a stored
  `source_fixture_id` (`keepStoredFixtureId`); lineups/games are rebuilt from the acta, with player
  lookups staying source-scoped (`findPlayerSeasonBySourceLicenseAndSeason`). Never add external ids to
  `FederatedClub` or `FederatedPlayer`.
- A PLAYED match is never downgraded: pending/partial/invalid actas on it keep today's regression/invalid
  reporting in every mode.
- Out of scope: surfacing amendments in the import preview (FEAT-00088 keeps `PLAYED_KEPT`; possible
  follow-up), a dedicated `ImportLifecycleCounters` component and its DTO/API ripple (amendments are
  visible as outcome counts, warnings and the audit log), a persisted audit table, amendment detection
  for SCHEDULED matches (already covered by the reschedule rule), and the backlog items FEAT-00090 to
  FEAT-00093.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P2, size M, Slice 4: hardening. Depends on: FEAT-00080 (T5).
- 2026-09-29: Build plan written against commit 281ad9a (FEAT-00088). Status `idea` → `planned`. Decisions:
  (1) the checksum is a versioned (`v1:`) SHA-256 of the canonical *built content* (`MatchContent`), not of
  the raw file, because BCNESA splits one file into several fixtures and formatting/renames must not
  trigger rewrites; (2) the amendment rule is a new pure `MatchLifecyclePlanner.planAmendment`, and the
  re-apply reuses FEAT-00080's `replaceMatchContent`; (3) PLAYED matches created or upgraded after this
  feature always carry a checksum, even with detection disabled, so opting in later has a baseline;
  (4) a stored PLAYED match without a (current-version) checksum adopts the incoming one as its baseline
  in write mode instead of being re-applied, so enabling detection never mass-rewrites legacy seasons;
  (5) the switch follows the repository's `write|report` convention (`--detect-amended-actas[=write|report]`,
  `tt.league.import.execution.amended-acta-detection`), disabled by default; (6) amendments surface as
  reportable outcomes (warnings), outcome counts and an INFO audit line on a dedicated logger, with no new
  `ImportLifecycleCounters` component and no persisted audit table.
- 2026-09-29: Acceptance criterion added: "Report mode performs the same detection without persistence
  writes" (from decision 5, matching the consolidation/backfill report-mode convention). Effort kept at
  medium; it may grow to large because the change spans five modules.
- 2026-09-29: Open questions: whether the preview (FEAT-00088) should flag amended actas (out of scope
  here, planner support makes it a small follow-up); whether operators want an amendment counter in
  `ImportLifecycleCounters`/API DTOs; analysis open question 3 (how often federations amend published
  actas) still decides whether api-runtime deployments should enable write mode.
- 2026-09-29: Effort raised from medium to large at the user's request: the change spans five modules
  (core-domain, core-repository-jpa, import, import-runtime, api-runtime) plus three documents.
- 2026-09-29: Implemented the plan: `MatchContentChecksum` (canonical `v1:` SHA-256 over the built
  content), `Match.sourceChecksum` + `MatchRepository.recordSourceChecksum`, the JPA `source_checksum`
  column/mappers/guarded update and backfill null-out, the in-memory adapter and test stubs,
  `MatchLifecyclePlanner.planAmendment` with `AmendedActaMode` and the new actions/outcomes, the writer's
  5-arg `apply` with checksum-on-create/upgrade and the `org.cttelsamicsterrassa.data.load.audit.AmendedActa`
  audit line, the `ImportExecutionOptions`/`ImportRunContext`/processor wiring, the `--detect-amended-actas`
  CLI flag, and the `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION` property. Tests added across all modules
  (checksum, domain invariant, JPA round-trip/guards/backfill, planner, writer + audit capture, preview
  mapping, run context, CLI/property parsing, and the `AmendedActaImportTest` end-to-end for RFETM and FCTT).
  Validation: `mvn test` from the root is green. Documentation updated (`rfetm-datamodel.md` and both
  runtime READMEs). Status `planned` → `in-review`.
