# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>` and `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>`. Test sources mirror them under `src/test/java`. Steps 1-7 are core-only and test with fakes; steps 8-15 add
the ingest status read, migration, persistence, the adaptive scheduler, the policy API and documentation. No
platform module, platform REST contract or `tt-league-ingest` code changes.

**Contracts used (checked against the code on 2026-10-05).**
- Ingest `GET /api/v1/ingest/sources/{source}/match-days-status?season=` (FEAT-00098, `X-API-Key`): 200 with
  `matchDays[]` rows `{season, category, group, phase, gender, territory, matchDay, status, matches, played,
  reported, firstMatchAt, lastMatchAt, file, contentUpdatedAt}` (`status` is `complete`/`partial`/`scheduled`/
  `future`, evaluated at the last download); 404 when the file or the season does not exist; 400 for an unknown
  source or malformed season. Rows are in the ingest scope vocabulary and round-trip as scopes (FEAT-00098 test):
  RFETM scopes support only `category` and `matchDays`; BCNESA `category`, `group`, `phase`, `territory`,
  `matchDays`; FCTT all six fields.
- Ingest scope contract (FEAT-00098): scopes are OR-combined, fields inside one scope AND-combined; a scoped run must
  be `mode=delta` (already chosen by `IngestRunRequest.forRun` for a non-full-season `RunScope`). Callers should send
  disjoint scopes.
- Platform match-day identity in the tracker (`MatchDayKey`: `competition`, `groupNumber`, `phase`, `round`) is
  derived by the import from the actas path:
  - RFETM: `competition = <category>-<sex>` (folders `<category>/<day>/<sex>/`), `groupNumber` = acta group,
    `round` = day folder, phase null.
  - FCTT: `competition = <category>-<masculino|femenino>` (`male`/`female` gender mapped), `groupNumber` from the
    group folder (`G?(\d+)`), `phase` from the acta payload `fase` (not the folder).
  - BCNESA: `competition` = category folder, or for `rtb-*` folders the legacy name from `BcnesaCompetitionNames`
    (`rtb-segona-a` → `Segona _A_`, 16 entries); `groupNumber` from `G(\d+)`, null for the Veterans "Other" group;
    `phase` = phase folder.
- `TriggerRun.Command` accepts `RunTrigger.SCHEDULED` with `ScopeType.GROUP` (single source, filters required) and
  `FULL_SEASON`; `ConflictMode.REJECT` skips a source with an active run. `OpenMatchDayScopeResolver` must return a
  non-empty scope or throw `ScopeUnavailableException` (`NO_OPEN_MATCH_DAYS` for nothing to ingest).
- The runtime's `UnavailableOpenMatchDayScopeResolver` is `@ConditionalOnMissingBean`; providing a resolver bean
  replaces it (FEAT-00105 follow-up).
- Roles arrive as `ROLE_<role>` authorities (`PlatformJwtAuthenticationConverter`), so the policy API uses
  `hasRole("ADMIN")`.

**Polling model (decisions this plan fixes).**
- A *poll unit* is one ingest group: the status-row identity without match days (`category`, `group`, `phase`,
  `territory`, `gender`, limited to the fields the source supports), plus the set of its open rounds as `matchDays`.
  RFETM units are therefore per category. Each source also has one `FULL_REFRESH` unit.
- `poll_schedule` holds one row per `(source, season, scope_key)`; `scope_key` is the SHA-256 hex of the canonical
  unit identity (match days excluded, so back-off state survives a new round being added) or the literal
  `FULL_REFRESH`.
- Candidate matches for polling come from the tracker: non-ignored matches with status `SCHEDULED`,
  `AWAITING_RESULT`, `OVERDUE` or `POSTPONED` on match days that are `OPEN`, or `CLOSED` with reason `ALL_RESOLVED`
  that still hold a `POSTPONED` match (FEAT-00107 keeps those tracked for this feature). Manually closed (`MANUAL`)
  and `REMOVED` days and `UPCOMING` days are never polled.
- Policy levels (defaults; every value configurable per source):

  | Level | Rule (evaluated in the schedule zone) | Default interval |
  | --- | --- | --- |
  | `MATCH_DAY` | a candidate match today and now ≥ first start today + `match-day-start-offset` (2 h) | `PT2H` |
  | `DAY_AFTER` | latest unresolved match date was yesterday | `PT3H` |
  | `DAYS_2_TO_7` | latest unresolved match date 2-7 days ago | `PT12H` |
  | `OPEN` | open, no match today (future, undated, or today before the offset) | `PT24H` |
  | `OVERDUE` | only `OVERDUE` matches left (or older than 7 days), youngest ≤ `overdue-stop-after` (21 days) | `PT24H` |
  | `STOPPED` | only `OVERDUE` matches older than `overdue-stop-after`; raises one alert | none |
  | `FULL_REFRESH` | the per-source full-season unit | `P7D` |

  The most urgent level of the unit's candidate matches wins. `OPEN` before the offset gets `nextRunAt` capped at
  the first start + offset, so a match day starts on time.
- Back-off: after `no-change-threshold` (3) consecutive `NO_CHANGES` outcomes the interval doubles, and doubles again
  every further threshold, capped at the interval of the next slower level (`MATCH_DAY` → `DAY_AFTER` →
  `DAYS_2_TO_7` → `OPEN`/`OVERDUE` → `FULL_REFRESH`). A level change resets the counter. `SUCCEEDED`/`PARTIAL`
  reset it; `FAILED` leaves it unchanged (the executor retry rule already handles failures).
- A run's outcome applies to every unit it covered (the import report is not per group); accepted imprecision.
- One adaptive tick per source launches at most one run: `FULL_REFRESH` when due (it covers every unit), otherwise
  one `GROUP` run whose scope is the union of the due units. Units are never run in parallel.
- Season start: the first tick for a `(source, season)` with no `FULL_REFRESH` row creates it due now, so a new
  season (or a new deployment) starts with a full-season run that also bootstraps the tracker.
- An open tracker match day that matches no status row fails the scope build with `SCOPE_UNMATCHED` (listing the
  keys); no silent widening. The weekly full refresh, which re-downloads every page and rewrites the status file,
  stays the recovery path.

1. **Settings and levels (`<core>/polling/`, new package).**
   - `PolicyLevel` enum: `MATCH_DAY`, `DAY_AFTER`, `DAYS_2_TO_7`, `OPEN`, `OVERDUE`, `STOPPED`, `FULL_REFRESH`,
     with `slower()` giving the cap level used by the back-off.
   - `PollingSettings(Duration matchDay, Duration matchDayStartOffset, Duration dayAfter, Duration daysTwoToSeven,
     Duration open, Duration overdue, int overdueStopAfterDays, Duration fullRefresh, int noChangeThreshold)`:
     all durations positive, `matchDay ≤ dayAfter ≤ daysTwoToSeven ≤ open ≤ fullRefresh`,
     `overdue ≤ fullRefresh`, `overdueStopAfterDays ≥ 1`, `noChangeThreshold ≥ 1`; `interval(PolicyLevel)`;
     `defaults()` with the table values.
   - `PollingPolicySettings(PipelineSource source, PollingSettings settings, long version, String updatedBy,
     Instant updatedAt)` for a stored per-source override.
2. **Status rows and source vocabulary (`<core>/polling/scope/`).**
   - `IngestStatusRow(String season, String category, String group, String phase, String gender, String territory,
     int matchDay, String status, LocalDateTime firstMatchAt, LocalDateTime lastMatchAt)` (blank strings → null,
     `matchDay ≥ 1`) and `IngestMatchDayStatus(PipelineSource source, String season, List<IngestStatusRow> rows)`.
   - `PlatformGroupKey(String competition, Integer groupNumber, String phase)` and
     `SourceVocabulary` (sealed, one implementation per source, chosen by `SourceVocabulary.of(source, names)`):
     - `Optional<PlatformGroupKey> platformKey(IngestStatusRow row)` mirrors the import rules above. Empty when the
       row cannot be mapped (unmapped `rtb-*` folder, group that is not `G?\d+`). `phase` is compared only for
       BCNESA; RFETM and FCTT keys carry no phase, so every phase row of the group is selected.
     - `PollUnit unit(IngestStatusRow row)`: the row as a scope identity restricted to the source's supported
       fields.
   - `BcnesaCompetitionNames` value in the core (folder → stored name map) loaded from configuration
     (`tt.pipeline.polling.bcnesa-competition-names`, default = the 16 import entries); comparison is
     case-insensitive on the folder, exact on the stored name. Do not import or depend on `tt-data-league-import`.
3. **Scope builder (`<core>/polling/scope/ScopeBuilder`).**
   - `ScopeBuild build(PipelineSource source, String season, List<OpenMatchDay> open, IngestMatchDayStatus status)`
     where `OpenMatchDay(MatchDayKey key, List<MatchTracking> candidates)`.
   - For each open day, select status rows with `platformKey(row)` equal to the day's group key (phase rule above)
     and `row.matchDay == key.round`. Group the selected rows by `PollUnit`, merging their rounds into sorted
     `matchDays`, and attach the candidate matches of every contributing day to the unit.
   - Days without a selected row are collected; if any, throw `ScopeBuildException(SCOPE_UNMATCHED, message)`
     naming at most 10 keys plus a count (user-facing, no secrets).
   - `ScopeBuild(List<PollUnitScope> units)` with `PollUnitScope(PollUnit unit, String scopeKey, ScopeFilter filter,
     List<MatchTracking> candidates)`; `scopeKey` = SHA-256 hex of the canonical identity string
     (`field=value` pairs in fixed order, null as empty, `|`-separated, UTF-8). Units are disjoint by construction.
   - `OpenMatchDays` helper: from `MatchDayRepository.findBySourceAndSeason` + `findMatches`, keep the days and
     candidate matches defined in the polling model.
4. **Polling policy (`<core>/polling/PollingPolicy`).** Pure function, no I/O:
   `PollDecision decide(PollState state, List<MatchTracking> candidates, PollingSettings settings, Instant now,
   ZoneId zone)` returning `level`, `baseInterval`, `effectiveInterval` (back-off applied), `nextRunAt` and
   `stopReason` (`OVERDUE_LIMIT` for `STOPPED`). Rules exactly as the polling model table; `nextRunAt` =
   `(lastRunAt ?? now) + effectiveInterval`, capped at the next first-start + offset for `OPEN`, and `now` for a new
   unit. `PollState` = the stored level, `consecutiveNoChange`, `lastRunAt`. Also
   `PollState applyOutcome(PollState, RunStatus terminal)` for the counter rules.
5. **Schedule aggregate and ports (`<core>/polling/`).**
   - `PollSchedule` immutable aggregate: id, source, season, scopeKey, `ScopeFilter` (null for `FULL_REFRESH`),
     level, interval, consecutiveNoChange, nextRunAt, lastRunAt, pendingRunId, stoppedAt, stopReason, alertedAt,
     version; methods `decided(PollDecision)`, `launched(UUID runId, Instant at)`, `outcome(RunStatus, Instant)`,
     `stopped(...)`, `resumed(Instant)`.
   - `port/PollScheduleRepository`: `findBySourceAndSeason`, `findById`, `save` (insert or versioned update,
     `StalePollScheduleException`), `delete(UUID)`, `query(source, season)`.
   - `port/PollPolicyRepository`: `find(PipelineSource)`, `findAll()`, `save(PollingPolicySettings, long
     expectedVersion)` (`StalePollPolicyException`), `delete(PipelineSource)`.
   - `port/IngestStatusGateway`: `Optional<IngestMatchDayStatus> matchDayStatus(PipelineSource, String season)`
     (empty on 404; other failures `GatewayException`). Kept separate from `IngestGateway` so the executor's port
     is unchanged.
   - `port/PollingAlerts`: `scopeStopped(PollSchedule)`, `scopeUnmatched(PipelineSource, String season, String
     message)`; implementations must not throw into the caller (FEAT-00112 replaces the logging adapter with
     notifications).
   - `PollingSettingsProvider`: stored override for the source, else the configured defaults.
6. **Open-match-day resolver (`<core>/polling/TrackerOpenMatchDayScopeResolver`).** Implements
   `OpenMatchDayScopeResolver`: open days → status read → `ScopeBuilder` → `RunScope` of every unit's filter
   (all units, not only due ones, because an operator asked for the open match days). No open day →
   `NO_OPEN_MATCH_DAYS`; no status file → `ScopeUnavailableException("NO_INGEST_STATUS", ...)`;
   `SCOPE_UNMATCHED` → `ScopeUnavailableException` with that code. `GatewayException` propagates as today.
7. **Adaptive tick (`<core>/polling/AdaptivePollingTick`).** `TickResult tick(PipelineSource source)` for the
   configured season, called only by the runtime scheduler:
   1. Apply finished runs: rows whose `pendingRunId` run is terminal get `outcome(...)` and `pendingRunId` cleared;
      a missing run clears it with a warning.
   2. Ensure the `FULL_REFRESH` row (due now when created). If it is due and has no pending run: trigger
      `FULL_SEASON`/`SCHEDULED`/`REJECT` with `requestedBy` `system:polling`; on `Created` mark it launched and
      return. A `Rejected` (`ACTIVE_RUN`) result leaves every row untouched.
   3. Otherwise build the scope (step 3). `SCOPE_UNMATCHED` → `PollingAlerts.scopeUnmatched` (once per distinct
      message per day, tracked through `alertedAt` on the `FULL_REFRESH` row) and return without a run; a missing
      status file → return (the full refresh writes it).
   4. Upsert one row per unit with `PollingPolicy.decide`; delete `GROUP` rows whose unit is no longer open.
      A new `STOPPED` decision records `stoppedAt`/`stopReason` and calls `scopeStopped` once (`alertedAt`).
   5. Due = not stopped, no pending run, `nextRunAt ≤ now`. If any: one `GROUP`/`SCHEDULED`/`REJECT` trigger with
      the due units' filters; on `Created` mark each due row launched with the run id.
   - `requestedBy` `system:polling` distinguishes adaptive runs from `system:scheduler` fixed runs.
   - Every write goes through `PollScheduleRepository.save`; a stale write aborts the tick for that source (logged,
     retried on the next tick), never a partial retry loop.
   - Test-jar fixtures: `InMemoryPollScheduleRepository`, `InMemoryPollPolicyRepository`,
     `ScriptedIngestStatusGateway`, `RecordingPollingAlerts`.
8. **Ingest status adapter (`<rt>/gateway/HttpIngestStatusGateway`).** Uses the existing ingest `RestClient`
   (`X-API-Key`), `GET /api/v1/ingest/sources/{source}/match-days-status?season=`, maps 404 to empty, other
   errors through `GatewayErrors`; private response records; never logs the key. Parse `firstMatchAt`/`lastMatchAt`
   as local date-times (`yyyy-MM-ddTHH:mm`).
9. **Migration `V5__adaptive_polling.sql`** (schema `pipeline`):
   - `poll_schedule(id uuid PK, source varchar(16) CHECK IN sources, season varchar(9) CHECK pattern,
     scope_key varchar(64) NOT NULL, kind varchar(16) CHECK IN ('FULL_REFRESH','GROUP'), filter jsonb NULL
     (RunScopeJson filter layout; NULL iff FULL_REFRESH), policy_level varchar(16) CHECK IN levels,
     interval_seconds bigint NULL CHECK > 0, consecutive_no_change int NOT NULL DEFAULT 0 CHECK ≥ 0,
     next_run_at timestamptz NULL, last_run_at timestamptz NULL, pending_run_id uuid NULL REFERENCES
     pipeline_run(id) ON DELETE SET NULL, stopped_at timestamptz NULL, stop_reason varchar(32) NULL,
     alerted_at timestamptz NULL, version bigint NOT NULL, created_at, updated_at)`, unique
     `(source, season, scope_key)`, index `(source, season, next_run_at)`.
   - `poll_policy(source varchar(16) PK, settings jsonb NOT NULL, version bigint NOT NULL, updated_by
     varchar(128) NOT NULL, updated_at timestamptz NOT NULL)`.
   - Update `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md` (two sections + migration history).
10. **Persistence (`<rt>/persistence/`).** `PollScheduleEntity`/`PollScheduleJpaRepository`/`JpaPollScheduleRepository`
    and `PollPolicyEntity`/`PollPolicyJpaRepository`/`JpaPollPolicyRepository`, `@Version`-checked like
    `JpaMatchDayRepository`; reuse `RunScopeJson` for `filter`; a small `PollingSettingsJson` for `settings`.
11. **Configuration (`PipelineOrchestratorProperties`, `application.yml`).** New `tt.pipeline.polling` group:
    `sources` (set of adaptive sources, default empty = adaptive off), `tick-interval` (`PT5M`),
    `lock-at-most-for` (`PT10M`), `lock-at-least-for` (`PT30S`), `defaults.*` (the table values),
    `bcnesa-competition-names` (map, default the 16 entries). Validation: a source must not have both a fixed
    `schedule` cron and adaptive polling ("source X has both a cron and adaptive polling"); adaptive polling
    requires `tt.pipeline.schedule.season` and `zone` (reused, no second season setting); defaults validated
    through `PollingSettings`.
12. **Scheduler (`<rt>/polling/`).** `AdaptivePollingTrigger` `SmartLifecycle` mirroring `ScheduledRunTrigger`:
    private single-thread `ThreadPoolTaskScheduler` (not a bean), fixed delay `tick-interval`, per-source ShedLock
    lock `pipeline-polling-<SOURCE>` through the existing `LockingTaskExecutor`, catches and logs per-source
    failures so one source cannot stop the others. `PollingConfiguration` wires the core beans, the
    `TrackerOpenMatchDayScopeResolver` bean (replacing the unavailable one for every source, adaptive or not), and
    a `LoggingPollingAlerts` (WARN, no secrets).
13. **Policy and schedule API (`<rt>/api/PollingController`).**
    - `GET /api/pipeline/polling/policies` and `GET .../policies/{source}`: effective settings per source with
      `overridden`, `version`, `updatedBy`, `updatedAt` (any authenticated user).
    - `PUT .../policies/{source}` (`ADMIN`): full settings body plus `version` (0 when no override exists);
      400 on validation errors, 409 `STALE_POLICY` on a version conflict; `updatedBy` = JWT subject.
    - `DELETE .../policies/{source}` (`ADMIN`): back to the configured defaults.
    - `GET /api/pipeline/polling/schedules?source=&season=` (authenticated): rows with level, interval,
      counters, next run, pending run and stop state.
    - `POST /api/pipeline/polling/schedules/{id}/resume` (`matches:write`): clears `STOPPED` and makes the unit due
      now; 404/409 as appropriate.
    - `SecurityConfiguration`: add the `PUT`/`DELETE` policy matchers with `hasRole("ADMIN")` and the resume
      matcher with `matches:write`; `ProblemDetail` + `code` errors like `MatchDaysController`.
14. **Tests.**
    - Core: `PollingSettingsTest` (validation, ordering), `SourceVocabularyTest` (RFETM/FCTT/BCNESA key rules incl.
      `rtb-*` names, Veterans "Other", unmapped folder, FCTT gender mapping), `ScopeBuilderTest` (round merging per
      unit, disjoint units, RFETM category granularity, `SCOPE_UNMATCHED`, postponed match on a closed day kept,
      manual close/ignored/upcoming excluded, stable `scopeKey`), `PollingPolicyTest` (every level and boundary,
      start offset cap, back-off doubling at 3/6 and cap at the next level, reset on level change and success,
      `FAILED` keeps the counter, overdue stop after 21 days), `AdaptivePollingTickTest` (season-start full refresh,
      weekly refresh, one union `GROUP` run for due units, no run when nothing due, active run rejected leaves rows
      untouched, outcome application, stopped units skipped and alerted once, resume, unmatched alert, closed
      units deleted), `TrackerOpenMatchDayScopeResolverTest` (scope, `NO_OPEN_MATCH_DAYS`, `NO_INGEST_STATUS`,
      `SCOPE_UNMATCHED`).
    - Runtime: `HttpIngestStatusGatewayTest` (200 mapping, 404 empty, 401/5xx `GatewayException`, key never in
      messages), `AdaptivePollingMigrationTest`, `JpaPollScheduleRepositoryTest`, `JpaPollPolicyRepositoryTest`
      (versions, constraints), `PipelineOrchestratorPropertiesTest` (cron+adaptive conflict, season/zone required,
      invalid defaults), `AdaptivePollingTriggerTest` (lock per source, lifecycle, failure isolation),
      `PollingApiWebTest` (ADMIN-only writes 403 otherwise, 400/409, resume with `matches:write`), and update
      `RunsApiWebTest`/`RunsApiIntegrationTest` where `OPEN_MATCH_DAYS` expected `SCOPE_UNAVAILABLE`.
15. **Documentation and validation.**
    - Runtime README: adaptive polling (policy table, back-off, stop/alert, full refresh, season start, coexistence
      with fixed cron, configuration keys and env vars, API endpoints and roles, `OPEN_MATCH_DAYS` behaviour and its
      error codes). Update both orchestrator `AGENTS.md` files (core `polling` package rules; runtime `polling/`
      scheduler rules) and `pipeline-datamodel.md`.
    - `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`, then the full `mvn test`; run the Testcontainers
      tests with Docker before closing. Review the diff for `target/` content and out-of-scope changes.

## Acceptance Criteria

- [x] A scope builder turns a source's open match days into ingest `scopes` (territory/category/group/phase/match days)
- [ ] `poll_schedule` stores the next run, interval, consecutive no-change count and policy level per source and scope hash
- [x] Poll intervals follow the proposal's policy table (configurable per source), double after 3 consecutive `NO_CHANGES` up to the next level, and stop with an alert for matches overdue beyond 21 days
- [x] A weekly full-scope run (and one at season start) refreshes fixtures, phases and re-draws
- [x] Admins can change the policy settings through an API; tests cover each policy level and the back-off
- [x] `OPEN_MATCH_DAYS` triggers resolve through the scope builder, with `NO_OPEN_MATCH_DAYS` when nothing is open

# Implementation Guidelines

- Respect federation sites: never shorten the ingest delays; scoped runs are the way to poll more often. One run per
  source at a time; units of a source are combined into one run, never run in parallel.
- Keep the core framework-free (`CoreDependencyRulesTest`): no Spring, JPA or HTTP types in `<core>/polling`.
  Time comes from `RunClock`; the zone is passed in.
- The orchestrator never depends on `tt-data-league-*`: the import's path-to-identity rules and the BCNESA name map
  are mirrored in `SourceVocabulary`/configuration, with tests that pin each rule. A change to those import rules
  must update the vocabulary in the same change.
- No silent widening: an unmatched open match day fails the scope build (`SCOPE_UNMATCHED`) instead of falling back
  to a full-season run; a missing status file is reported, not guessed.
- New runs only through `TriggerRun`; adaptive runs use `RunTrigger.SCHEDULED`, `ConflictMode.REJECT` and
  `requestedBy` `system:polling`. Fixed cron (FEAT-00106) stays as the fallback mode; a source uses one or the other.
- Schema changes only through `V5__adaptive_polling.sql`; never edit `V1`-`V4`. No new `Executor`/`TaskScheduler`
  beans and no `@EnableScheduling`.
- The tracker stays the only source of match state; the policy reads `MatchTracking` statuses and dates and never
  re-derives overdue or postponed itself.
- Out of scope: per-group import reports, ETag/conditional downloads, notification channels (FEAT-00112),
  frontend views for policies and schedules (FEAT-00110/FEAT-00111), metrics (FEAT-00115), any ingest or platform
  change.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Adaptive polling" and "Scope building". Rollout phase 3 exit: runs stop by themselves when a match day is complete.

## 2026-10-05 — Plan built (`idea` → `planned`)

- **Scope join (user decision).** Tracker match days use platform identity; ingest scopes use folder vocabulary.
  The scope builder reads the ingest `match-days-status` endpoint (rows already in scope vocabulary, round-trip
  tested in FEAT-00098) and joins them to open tracker match days with per-source key rules in the core (RFETM/FCTT
  `<category>-<sex>`; BCNESA folder plus a configured `rtb-*` name map). An open day with no matching row fails with
  `SCOPE_UNMATCHED`. Rejected alternatives: ingest emitting the platform key (mirrors Java naming in Python) and
  status rows only (manual closes/ignores and the platform grace period would not affect polling).
- Poll unit = ingest group with merged open rounds; `scope_key` excludes match days so back-off survives new rounds.
  RFETM scopes support only `category`/`matchDays`, so RFETM units are per category.
- One run per tick per source: the full refresh when due, else one `GROUP` run with the union of due units. A run's
  outcome counts for every unit it covered.
- The weekly full refresh is a `FULL_REFRESH` row in `poll_schedule`; "season start" is the first tick for a
  source/season without that row. Fixed cron and adaptive polling are mutually exclusive per source.
- Settings: configured defaults plus per-source overrides in `poll_policy`, changed through an `ADMIN` API with
  optimistic versions. Alerts go through a `PollingAlerts` port with a logging adapter until FEAT-00112.
- Added the `OPEN_MATCH_DAYS` resolver criterion: the FEAT-00105 follow-up asks this feature to replace the
  unavailable resolver.
- Open: verify the BCNESA phase comparison (status row phase comes from page metadata, platform phase from the
  folder) and the FCTT `male`/`female` gender values against a real download; adjust `SourceVocabulary` tests if
  they differ.

## 2026-10-05 — Approved (`planned` → `ready`)

- Plan approved for implementation as written, including the status-row scope join with `SCOPE_UNMATCHED`, one run
  per source per tick, the `FULL_REFRESH` row for weekly and season-start runs, and the `poll_policy` admin API.

## 2026-10-05 — Implemented (`ready` → `in-progress` → `in-review`)

Delivered as planned (core `polling` package with `scope` and `port`, runtime `HttpIngestStatusGateway`, `V5__adaptive_polling.sql`,
JPA adapters, `tt.pipeline.polling` configuration, `AdaptivePollingTrigger`, `PollingController`, README, both
`AGENTS.md` files and `pipeline-datamodel.md`). Deviations and decisions made while building:

- **Resume.** A resumed unit clears `stopped_at`, `stop_reason` and `alerted_at`, goes back to `OVERDUE` and forgets
  `last_run_at`. The policy never stops a unit that has no last run, so a resumed (or brand new) unit is polled once
  before it can stop again; that needs no extra column. `PollSchedule.stopped(...)` from the plan is folded into
  `decided(...)`.
- **Stop rule.** `STOPPED` applies only to matches whose tracked status is `OVERDUE` and older than
  `overdue-stop-after-days`. Other matches older than 7 days (for example a `POSTPONED` one) stay at the `OVERDUE`
  level and keep backing off up to the weekly interval; they never raise the stop alert.
- **Unmatched alert dedupe.** `alertedAt` of the `FULL_REFRESH` row is a timestamp, so it cannot hold the message. The
  tick alerts once per local day, and again when the message changes (the last message per source is kept in memory,
  so a restart can alert once more).
- **Ports.** `PollPolicyRepository.save` takes `(source, settings, updatedBy, updatedAt, expectedVersion)` and returns
  the stored `PollingPolicySettings` (version `expectedVersion + 1`) and `delete` returns whether an override existed.
  `PollScheduleRepository.query` does not filter on a null argument. `PollingSettingsProvider.effective` exposes the
  override metadata for the API.
- **Open match days.** `OpenMatchDays` drops a day whose candidate list is empty (nothing to poll), so it also cannot
  fail the scope build. The `PollingPolicy` caps an `OPEN` unit at the next first start plus offset of any dated
  unresolved match after the last run, not only today's.
- **Resolver bean.** `UnavailableOpenMatchDayScopeResolver` and its `@ConditionalOnMissingBean` were removed;
  `PollingConfiguration` always provides `TrackerOpenMatchDayScopeResolver`, so `SCOPE_UNAVAILABLE` is no longer
  produced by the runtime (the `RunsApiWebTest` outcome mapping test keeps using the code through a mocked outcome).
- **API.** Durations serialize in the canonical ISO form (`PT168H` for a week); `P7D` is accepted on input.
  `PUT`/`DELETE` policy answer `200` with the effective policy. The resume endpoint answers `404`
  `POLL_SCHEDULE_NOT_FOUND`, `409` `NOT_STOPPED` or `409` `STALE_SCHEDULE`.
- **Open question still open.** The BCNESA phase comparison and the FCTT `male`/`female` values were pinned from the
  ingest status code and the import rules, not against a real download; adjust `SourceVocabularyTest` if a real
  `match-days-status.json` differs.

**Validation (2026-10-05).** `mvn -pl tt-league-pipeline-orchestrator-core -am test` and `mvn -pl
tt-league-pipeline-orchestrator-runtime -am test` pass. **Docker is not available in this environment, so every
Testcontainers test was skipped, including the new `AdaptivePollingMigrationTest`, `JpaPollScheduleRepositoryTest`
and `JpaPollPolicyRepositoryTest` and the full-context `PipelineOrchestratorApplicationTest`.** The V5 migration,
the JPA mappings and the full application context are therefore not verified against PostgreSQL yet; the acceptance
criterion on `poll_schedule` stays unchecked until those tests have run with Docker.
