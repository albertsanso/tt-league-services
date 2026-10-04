# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>` and `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>`. Test sources mirror them under `src/test/java`. Steps 1-6 are core-only and test with fakes; steps 7-14 add the
HTTP gateway, migration, persistence, recompute triggers, REST API and documentation. No platform module,
platform REST contract or `tt-league-ingest` code changes.

**Contracts used (checked against the code on 2026-10-05).**
- `GET /api/v1/match/round-progress?source=&season=` (FEAT-00102), called **without** `onlyOpen` so tracked match
  days that the platform no longer reports as open can still be closed. Per jornada: `competition`, `groupNumber`,
  `phase`, `round`, `firstDate`, `lastDate`, `scheduledMatches`, `playedMatches`, `open`; top level `today`
  (Europe/Madrid) and `overdueGraceDays`. `competition`, `groupNumber` and `phase` can be null.
- `GET /api/v1/match/calendar?source=&season=&competition=` (FEAT-00092): every match of one competition, grouped
  by `groupNumber`/`phase` and `round`, each with `id`, `dateTime`, `homeTeamName`, `awayTeamName`, `status`
  (`SCHEDULED`/`PLAYED`) and `calendarState` (`PLAYED`, `OVERDUE`, `POSTPONED`, `UNDATED`, `AWAITING_RESULT`,
  `UPCOMING`). Undated matches are included; `competition` is required; the `group` filter drops null groups, so it
  is not used. `GET /calendar/range` is **not** used: it omits undated matches and caps the range at 62 days, so a
  postponed match from an old jornada could fall outside it.
- Both endpoints need `matches:read`; the orchestrator calls them with the existing `platformRestClient`
  (`X-API-Key` = `PIPELINE_PLATFORM_API_KEY`), so that service credential must list `imports:write,matches:read`.
- `RunObserver.runChanged` is called on the run thread after every `runs.update`; it must only enqueue work.
  `CompositeRunObserver` already isolates observer failures.
- PostgreSQL 16 in tests; the migration avoids `NULLS NOT DISTINCT` and uses a `COALESCE` expression index so it
  also runs on older servers.

**Tracker model (decisions this plan fixes).**
- A *match day* is one platform jornada: key `(source, season, competition, groupNumber, phase, round)`.
- Window: `start = firstDate`, `end = lastDate + overdueGraceDays` (the last `AWAITING_RESULT` day, same rule as the
  FEAT-00102 `open` flag); an undated jornada has no window.
- Match-day states: `UPCOMING` (window not started, or undated), `OPEN`, `CLOSED`. A match day is created only when
  the platform reports its jornada `open` (or it is already tracked); jornadas that closed before the tracker
  first saw them are never created.
- Opens when `today >= start`, or earlier as soon as any of its matches is `AWAITING_RESULT`, `OVERDUE` or
  `REPORTED`.
- Match status mapping from `calendarState`: `PLAYED` -> `REPORTED`, `AWAITING_RESULT` -> `AWAITING_RESULT`,
  `OVERDUE` -> `OVERDUE` (derived or manual mark), `POSTPONED` -> `POSTPONED`, `UPCOMING`/`UNDATED` -> `SCHEDULED`;
  any other value is a protocol error. The tracker never derives states itself.
- A match is *resolved* when it is `REPORTED`, `POSTPONED` or ignored. An `OPEN` match day with at least one
  match auto-closes (`ALL_RESOLVED`) once every match is resolved; `OVERDUE`, `AWAITING_RESULT` and `SCHEDULED`
  keep it open. Several match days per source can be `OPEN` at once.
- A match day closed by the tracker (`ALL_RESOLVED`) reopens automatically if a later recompute finds an
  unresolved match (re-draw, correction). A manually closed one (`MANUAL`) reopens only through the reopen action.
  A tracked jornada missing from round progress is closed with `REMOVED`, and its matches are removed.
- `POSTPONED` matches stay tracked under their original match day after it closes and keep being refreshed while
  the platform reports their jornada `open`, so FEAT-00108 can still poll them.
- `reported_at`/`reported_run_id` are set when a match first becomes `REPORTED` and never overwritten while it
  stays `REPORTED`. After-run recomputes use the run's `finishedAt` and id; periodic recomputes use the recompute
  time and a null run id (the result came from an import the orchestrator did not drive). If a match leaves
  `REPORTED`, both values are cleared and set again on the next report.
- Every lifecycle change and manual action appends a `match_day_event` (who, when, optional run id and note).
  System changes use actor `system:tracker`.

1. **Core values (`<core>/tracker/`).**
   - `MatchDayKey(PipelineSource source, String season, String competition, Integer groupNumber, String phase,
     int round)`: season via `PipelineRun.requireValidSeason`, competition non-blank (max 255), phase optional
     non-blank (max 255), `groupNumber >= 1` when set, `round >= 1`.
   - `MatchDayWindow(LocalDate firstDate, LocalDate lastDate, int graceDays)`: both dates null or both set,
     `firstDate <= lastDate`, `graceDays >= 0`; `start()`, `end()` (`lastDate + graceDays`), `isDated()`,
     `hasStarted(LocalDate today)`, `contains(LocalDate today)`; `undated(graceDays)` factory.
   - Enums `MatchDayState {UPCOMING, OPEN, CLOSED}`, `CloseReason {ALL_RESOLVED, MANUAL, REMOVED}`,
     `TrackedMatchStatus {SCHEDULED, AWAITING_RESULT, REPORTED, POSTPONED, OVERDUE}`,
     `MatchDayEventKind {OPENED, CLOSED, REOPENED, MATCH_REPORTED, MATCH_IGNORED, MATCH_UNIGNORED, MATCH_REMOVED,
     NOTE}`.
   - Package-private `TrackerChecks` (same helpers as `run/Checks`, which is package-private there).

2. **Core aggregates (`<core>/tracker/`).** Immutable, every change returns a new instance, like `PipelineRun`.
   - `MatchDay`: `id`, `key`, `window`, `state`, `closeReason`, `closedAt`, `closedBy`, `openedAt`, `createdAt`,
     `lastRecomputedAt`, `version`. Invariant: `state == CLOSED` iff `closeReason`, `closedAt` and `closedBy` are
     set. Transitions `open(now)`, `close(reason, actor, now)`, `reopen(now)`, `withWindow(window)`,
     `recomputed(now)`; illegal ones (close a closed day, reopen a non-closed one) throw
     `IllegalMatchDayTransitionException`.
   - `MatchTracking`: `matchId` (platform UUID), `matchDayId`, `status`, `matchDateTime` (`Instant`, nullable),
     `homeTeamName`, `awayTeamName`, `firstSeenAt`, `statusChangedAt`, `lastSeenAt`, `reportedAt`,
     `reportedRunId`, `ignoredAt`, `ignoredBy`, `version`. Invariants: `status == REPORTED` iff `reportedAt` set;
     `ignoredAt`/`ignoredBy` both set or both null. `isResolved()`, `isIgnored()`, `observe(...)` (applies the
     reported-at rule above), `ignore(actor, now)`, `unignore()`, `moveTo(matchDayId)`.
   - `MatchDayEvent` record: `id`, `matchDayId`, `matchId` (nullable), `kind`, `actor` (max 128), `occurredAt`,
     `runId` (nullable), `note` (max 2000; required for `NOTE`).

3. **Core rules (`<core>/tracker/TrackerRules`).** Pure static functions, one place for each rule:
   `TrackedMatchStatus mapCalendarState(String)` (throws `IllegalArgumentException` on unknown values),
   `MatchDayState targetState(MatchDay, List<MatchTracking>, LocalDate today)` (opening and auto-close rules above),
   `boolean canAutoClose(List<MatchTracking>)` and `boolean shouldReopen(MatchDay, List<MatchTracking>)`
   (only `ALL_RESOLVED` days).

4. **Core ports (`<core>/tracker/port/`).**
   - `PlatformMatchGateway` with `PlatformRoundProgress roundProgress(PipelineSource, String season)` and
     `PlatformCompetitionCalendar competitionCalendar(PipelineSource, String season, String competition)`; failures
     are the existing `GatewayException`.
   - Gateway values: `PlatformRoundProgress(LocalDate today, int overdueGraceDays, List<PlatformJornada>)`,
     `PlatformJornada(competition, groupNumber, phase, round, firstDate, lastDate, scheduledMatches,
     playedMatches, open)`, `PlatformCompetitionCalendar(LocalDate today, List<PlatformCalendarMatch>)`,
     `PlatformCalendarMatch(UUID id, competition, groupNumber, phase, round, Instant dateTime, homeTeamName,
     awayTeamName, String status, String calendarState)`. Plain JDK types only (D2: no platform DTO reuse).
   - `MatchDayRepository`: `Optional<MatchDay> findById(UUID)`, `List<MatchDay> findBySourceAndSeason(source,
     season)`, `List<MatchTracking> findMatches(Collection<UUID> matchDayIds)`,
     `Optional<MatchTracking> findMatch(UUID matchId)`, `List<MatchDayEvent> findEvents(UUID matchDayId)`,
     `Set<SourceSeason> findSourceSeasonsWithUnclosedDays()`, `MatchDayPage query(MatchDayQuery)`, and
     `void apply(MatchDayChangeSet)` that writes inserted/updated days, upserted/removed matches and appended events
     atomically and throws `StaleMatchDayException` on a version conflict. `MatchDayQuery`: optional source, season,
     state, `from`/`to` (window overlap), page/size (size 1-200), sorted by `firstDate` nulls last, then key.
     Supporting records: `SourceSeason(source, season)`, `MatchDayPage(items, total, page, size)` with per-status
     match counts per item, and `MatchDayChangeSet(days, matches, removedMatchIds, events)`.

5. **Core recompute (`<core>/tracker/MatchDayTracker`).**
   `RecomputeOutcome recompute(PipelineSource source, String season, RunRef run)` (`RunRef(UUID runId, Instant
   finishedAt)`, null for periodic):
   1. `roundProgress(...)`; select jornadas that are `open` or match an unclosed tracked day; jornadas with a null
      competition are skipped with a `WARNING` log and counted in the outcome (`/calendar` cannot read them).
   2. `competitionCalendar(...)` once per distinct competition of the selection.
   3. Consistency check, before any write: both responses have the same `today`, and each selected jornada's
      `scheduledMatches + playedMatches` equals its calendar match count. On mismatch throw
      `TrackerInconsistencyException` (an import ran in between); nothing is written and the next recompute repairs it.
   4. Build a `MatchDayChangeSet`: create new days, update windows, upsert matches (moving a match whose round
      changed), remove matches no longer in their jornada (`MATCH_REMOVED`), close removed jornadas (`REMOVED`),
      apply `targetState` and `shouldReopen` with events, set `lastRecomputedAt`.
   5. `repository.apply(changeSet)`; return `RecomputeOutcome(source, season, created, updated, opened, closed,
      reopened, reported, skippedGroups)`. Time comes from `RunClock.now()`; "today" always from the platform.

6. **Core manual actions (`<core>/tracker/MatchDayActions`).** `close(matchDayId, actor, note)`,
   `reopen(matchDayId, actor, note)`, `ignoreMatch(matchDayId, matchId, actor, note)`,
   `unignoreMatch(matchDayId, matchId, actor, note)`, `addNote(matchDayId, matchId /*nullable*/, actor, text)`.
   Each loads, applies the aggregate transition, appends the event with actor and `RunClock.now()`, re-applies the
   auto-close rule (ignoring the last unresolved match closes the day as `ALL_RESOLVED` with a second,
   `system:tracker` event) and saves through `apply`. Unknown ids -> `MatchDayNotFoundException`; a match of
   another day -> `MatchDayNotFoundException`; illegal transitions -> `IllegalMatchDayTransitionException`
   (close/reopen of the wrong state, ignore an ignored match, unignore a not-ignored one).

7. **Core observer and test fixtures.**
   - `<core>/tracker/TrackerRunObserver implements RunObserver`: on `runChanged` with a terminal status
     (`NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`) calls a `RecomputeRequests` port
     (`request(source, season, RunRef)`); ignores non-terminal statuses and step changes.
   - Add to the core `test-jar` (`src/test/java/.../execution/testing`): `InMemoryMatchDayRepository` (with version
     checks) and `ScriptedPlatformMatchGateway`.
   - Add `tracker/package-info.java`; `CoreDependencyRulesTest` already covers the new package unchanged.

8. **HTTP gateway (`<rt>/gateway/HttpPlatformMatchGateway`).** Uses the existing `platformRestClient`; builds both
   URIs with `uriBuilder` query params (competition names with spaces and accents are encoded); private response
   records mirror only the fields in "Contracts used" and ignore unknown fields. Errors go through
   `GatewayErrors.translate`; a missing required field or unparseable value -> `GatewayErrors.protocol`. A `403`
   message already says "check the configured API key".

9. **Flyway `V4__match_day_tracker.sql`** (schema `pipeline`, no reference to platform tables):
   - `match_day`: `id uuid PK`, `source varchar(16)` (CHECK as `pending_trigger`), `season varchar(9)` (CHECK
     pattern), `competition varchar(255) NOT NULL`, `group_number integer NULL CHECK (>= 1)`, `phase varchar(255)
     NULL`, `round integer NOT NULL CHECK (>= 1)`, `first_date date`, `last_date date`, `grace_days integer NOT NULL
     CHECK (>= 0)`, `state varchar(16)` CHECK, `close_reason varchar(16)` CHECK, `closed_at timestamptz`,
     `closed_by varchar(128)`, `opened_at timestamptz`, `created_at`, `last_recomputed_at timestamptz NOT NULL`,
     `version bigint NOT NULL`; CHECKs for the date pair, `first_date <= last_date` and the closed-state invariant.
     `UNIQUE INDEX ux_match_day_key ON (source, season, competition, COALESCE(group_number, 0),
     COALESCE(phase, ''), round)`; `ix_match_day_source_season_state (source, season, state)`;
     `ix_match_day_first_date (first_date)`.
   - `match_tracking`: `match_id uuid PK` (platform id, no FK), `match_day_id uuid NOT NULL REFERENCES match_day`,
     `status varchar(16)` CHECK, `match_date_time timestamptz`, `home_team_name`/`away_team_name varchar(255)`,
     `first_seen_at`, `status_changed_at`, `last_seen_at timestamptz NOT NULL`, `reported_at timestamptz`,
     `reported_run_id uuid REFERENCES pipeline_run(id)`, `ignored_at timestamptz`, `ignored_by varchar(128)`,
     `version bigint NOT NULL`; CHECKs for the reported and ignored invariants; `ix_match_tracking_day
     (match_day_id)`.
   - `match_day_event`: `id uuid PK`, `match_day_id uuid NOT NULL REFERENCES match_day`, `match_id uuid` (no FK:
     it survives `MATCH_REMOVED`), `kind varchar(32)` CHECK, `actor varchar(128) NOT NULL`, `occurred_at
     timestamptz NOT NULL`, `run_id uuid REFERENCES pipeline_run(id)`, `note varchar(2000)`; CHECK `kind <> 'NOTE'
     OR note IS NOT NULL`; `ix_match_day_event_day (match_day_id, occurred_at)`.

10. **Persistence (`<rt>/persistence/`).** `MatchDayEntity`, `MatchTrackingEntity`, `MatchDayEventEntity` (JPA
    `@Version` on the first two), Spring Data repositories and `JpaMatchDayRepository implements
    MatchDayRepository`. `apply` is one `@Transactional` method; an `OptimisticLockingFailureException` or a
    `ux_match_day_key` violation becomes `StaleMatchDayException`. `ddl-auto` stays `validate`.

11. **Recompute triggers (`<rt>/tracker/`).**
    - `TrackerRecomputeDispatcher implements RecomputeRequests, SmartLifecycle`: one private single-thread
      executor (never an `Executor` bean, see the runtime `AGENTS.md`) that runs `MatchDayTracker.recompute` in
      request order, so recomputes of one source/season never overlap in this instance. Failures
      (`GatewayException`, `TrackerInconsistencyException`, `StaleMatchDayException`) are logged with source,
      season and run id at `WARN` and dropped; the next trigger repairs the state. Requests after `stop()` are
      rejected with a log line.
    - `TrackerRecomputeSchedule implements SmartLifecycle`: private `ThreadPoolTaskScheduler` (pool 1) at a fixed
      delay of `tt.pipeline.tracker.recompute-interval`, under the ShedLock lock `pipeline-tracker-recompute`
      through the existing `LockingTaskExecutor`. Each tick enqueues a null-run recompute for every
      `findSourceSeasonsWithUnclosedDays()` pair plus `schedule.season` x each scheduled source when a schedule is
      configured. Nothing tracked and no schedule -> the tick does nothing (documented: the first run of a source
      bootstraps its tracking).
    - `TrackerConfiguration`: beans for `HttpPlatformMatchGateway`, `JpaMatchDayRepository`, `MatchDayTracker`,
      `MatchDayActions`, the dispatcher and the schedule; add `TrackerRunObserver` to the `CompositeRunObserver`
      list in `RunExecutionConfiguration.runObserver` (after the broadcaster).
    - `PipelineOrchestratorProperties.Tracker(Duration recomputeInterval, Duration lockAtMostFor,
      Duration lockAtLeastFor)`, all positive, `lockAtLeastFor <= lockAtMostFor`; `application.yml` defaults
      `PIPELINE_TRACKER_RECOMPUTE_INTERVAL:PT1H`, `PIPELINE_TRACKER_LOCK_AT_MOST_FOR:PT10M`,
      `PIPELINE_TRACKER_LOCK_AT_LEAST_FOR:PT30S` (tuning values, so defaults are allowed).

12. **REST API (`<rt>/api/MatchDaysController`, DTOs, `ApiExceptionHandler`).** Base `/api/pipeline/match-days`.
    - `GET ?source=&season=&state=&from=&to=&page=&size=` -> `PageDto<MatchDaySummaryDto>` (key, window, state,
      close reason/by/at, counts per tracked status, ignored count). Any authenticated user.
    - `GET /{id}` -> `MatchDayDetailDto` (summary + matches with status, date, teams, reported at/run, ignored
      by/at + events, oldest first). Any authenticated user.
    - `POST /{id}/close`, `POST /{id}/reopen` (body `{ "note": optional }`),
      `PUT /{id}/matches/{matchId}/ignore`, `DELETE /{id}/matches/{matchId}/ignore` (body `{ "note": optional }`),
      `POST /{id}/notes` (`{ "text": required, "matchId": optional }`). Actor from `CurrentUser.name`; responses
      return the updated `MatchDayDetailDto`.
    - `SecurityConfiguration`: `POST`/`PUT`/`DELETE` on `/api/pipeline/match-days/**` require `matches:write` (the
      existing `TRIGGER_AUTHORITY` value); `GET` falls under `anyRequest().authenticated()`.
    - Errors: invalid parameters/body (bad source, season, state, dates, blank note text, note over 2000) -> 400;
      `MatchDayNotFoundException` -> 404; `IllegalMatchDayTransitionException` and `StaleMatchDayException` -> 409,
      all as `ErrorDto` with stable codes (`MATCH_DAY_NOT_FOUND`, `ILLEGAL_TRANSITION`, `STALE_MATCH_DAY`).

13. **Tests** (JUnit 5, AssertJ, existing harnesses):
    - Core: `MatchDayWindowTest` (start/end, grace boundary day and day after, zero grace, undated, invalid pairs);
      `TrackerRulesTest` (every `calendarState` mapping and an unknown value, opening by date and by a reported
      match, auto-close with reported/postponed/ignored mixes, `OVERDUE`/`AWAITING_RESULT` keep it open, empty day,
      reopen only for `ALL_RESOLVED`); `MatchDayTest`/`MatchTrackingTest` (invariants, transitions, reported-at kept
      on later observations and cleared when leaving `REPORTED`); `MatchDayTrackerTest` with the FCTT 2026-2027 shape
      (`TERCERA`, groups 1-2, phase `1a Fase`, rounds 1-3: a postponed round-1 match, an `AWAITING_RESULT` and an
      `OVERDUE` round-3 match, an undated jornada), covering creation only for platform-open jornadas, two open
      days at once, reported-at from the first run vs a later run vs a periodic recompute, auto-close, auto-reopen,
      manual close not reopened, removed match and jornada, match moved between rounds, null competition skipped,
      inconsistency writes nothing; `MatchDayActionsTest` (each action records actor and time, ignoring the last
      open match auto-closes, illegal transitions, stale version); `TrackerRunObserverTest` (terminal statuses only).
    - Runtime: `HttpPlatformMatchGatewayTest` with `StubHttpServer` (query params and encoding, `X-API-Key` sent,
      mapping, 403 -> `REJECTED`, 5xx -> `UNAVAILABLE`, missing field/unknown state -> `PROTOCOL`);
      `MatchDayTrackerMigrationTest` (constraints, unique key with null group and phase); `JpaMatchDayRepositoryTest`
      (`apply` atomicity, version conflict -> `StaleMatchDayException`, queries and paging);
      `MatchDaysApiWebTest` (401, 403 without `matches:write` on every mutation, 200 reads for any user, 400/404/409,
      actor taken from the JWT); `TrackerRecomputeDispatcherTest` and `TrackerRecomputeScheduleTest` (lock name,
      targets, failures logged not thrown); `PipelineOrchestratorPropertiesTest` cases for the tracker settings;
      `TrackerRecomputeIntegrationTest` (Testcontainers + `StubHttpServer`): a run reaching `SUCCEEDED` produces
      `match_day`/`match_tracking` rows with `reported_run_id` set.

14. **Documentation and validation.**
    - `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`: sections for the three tables (columns,
      constraints, state rules, who writes them) and a `V4` line in the migration history.
    - `tt-league-pipeline-orchestrator-runtime/README.md`: "Match-day tracker" section (triggers, rules, endpoints and
      authorities), new `PIPELINE_TRACKER_*` rows, and that `PIPELINE_PLATFORM_API_KEY` must hold
      `imports:write,matches:read`.
    - Core and runtime `AGENTS.md`: tracker package boundaries (status mapping only in `TrackerRules`, the tracker
      never derives platform states, recomputes only through the dispatcher, no new `Executor` beans).
    - `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` with Docker running, then the full `mvn test`;
      review the diff for `target/` content and out-of-scope changes.

## Acceptance Criteria

- [ ] After every final run state and on a periodic recompute, the tracker reads round progress and calendar data from the platform and upserts `match_day` and `match_tracking` rows
- [ ] Match statuses `SCHEDULED`, `AWAITING_RESULT`, `REPORTED`, `POSTPONED` and `OVERDUE` follow the platform's derived states and grace period; `reported_at` records the first run that saw the result
- [ ] A match day closes when every match is reported, ignored or postponed out of its window; several match days can be open at once
- [ ] Operators with `matches:write` can close a match day manually, mark a match ignored and add a note, and each action records who and when
- [ ] Tests cover window calculation, postponed matches, closing rules and manual actions

# Implementation Guidelines

- Never store results; the platform owns them. Tracking rows hold operational state only.
- `CANCELLED`/`WALKOVER` are out of scope until the domain supports them (open question in the baseline item).
- The platform is the only source of match states: map `calendarState`, never re-derive postponed, overdue or
  awaiting-result from dates or rounds in the orchestrator. The grace period comes from the platform response.
- Every platform read is source- and season-scoped; competitions are read only for the selected jornadas.
- Platform match ids are plain references: no foreign keys or joins to platform tables, and match ids in
  `match_day_event` survive removal.
- Recomputes run only through `TrackerRecomputeDispatcher`; the run observer only enqueues and never blocks or throws
  into the executor. No new `Executor`/`TaskScheduler` beans and no `@EnableScheduling`.
- An inconsistent or failed recompute writes nothing; do not add retries, partial writes or fallbacks to
  `calendar/range`.
- Out of scope: the `OPEN_MATCH_DAYS` scope resolver and polling policy (FEAT-00108), frontend views
  (FEAT-00111), alerts (FEAT-00112), statistics (FEAT-00113), a manual recompute endpoint, and any platform change.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Match-day detection and pending tracking". Domain events are deferred: the orchestrator recomputes after each run it drives.

## 2026-10-05 — Plan built (`idea` → `planned`)

- Per-match rows come from `GET /api/v1/match/calendar` per competition, not `calendar/range` as the FEAT-00102 notes
  anticipated: the range read drops undated matches and is limited to 62 days, which would lose postponed matches
  of old jornadas. Round progress is read without `onlyOpen` so already tracked days can be closed or reopened.
- Ignoring a match is a flag next to the derived status, not a sixth status, so the platform state keeps updating
  and an ignore can be undone. Close/reopen/ignore/unignore/note are all recorded in an append-only
  `match_day_event` table, which also gives FEAT-00111 its timeline and FEAT-00112 its "match day closed" trigger.
- Reopen and unignore actions are included beyond the acceptance criteria so an operator mistake is reversible.
- Read endpoints (list and detail) are included because the manual actions need them and FEAT-00111 builds on them.
- The periodic recompute needs no new season setting: it covers every source/season with unclosed match days plus
  the fixed-schedule season. A source is bootstrapped by its first run.
- Deployment note: the platform service credential used by the orchestrator must add `matches:read`
  (`SECURITY_SERVICECREDENTIALS_<n>_PERMISSIONS=imports:write,matches:read`); otherwise every recompute fails with a
  403 that names the API key.
- Open question: groups with a null competition cannot be read through `/calendar` and are skipped with a warning.
  If any source produces them, a platform `competition`-optional calendar read would be a follow-up.

## 2026-10-05 — Approved (`planned` → `ready`)

- Plan approved for implementation as written, including the season-calendar read per competition, the
  ignore-as-flag model, the reopen/unignore actions and the read endpoints.

## 2026-10-05 — Implemented (`ready` → `in-progress` → `in-review`)

Implementation follows the plan; deviations and discoveries:

- **Round progress payload.** The platform returns round progress nested (`groups[]` with `competition`, `groupNumber`,
  `phase` and `rounds[]` carrying `round`, dates, `scheduledMatches`, `playedMatches`, `open`), not as a flat jornada
  list. `HttpPlatformMatchGateway` flattens it into the core `PlatformJornada` values; the core port is unchanged.
  `scheduledMatches` counts every non-played match (postponed and overdue included), so
  `scheduledMatches + playedMatches` equals the calendar match count used by the consistency check.
- **Error bodies.** The controllers answer with `ProblemDetail` plus a `code` property (`MATCH_DAY_NOT_FOUND`,
  `ILLEGAL_TRANSITION`, `STALE_MATCH_DAY`), the format the existing API handler already uses, instead of `ErrorDto`.
- **`from`/`to` filter.** It compares with the first and last match dates (grace period not added) so the JPA query
  stays a plain specification; undated match days are excluded whenever either bound is set.
- **Actions.** `reopen` does not re-run the auto-close rule (it would undo itself at once); a reopened day whose
  matches are all resolved closes again on the next recompute. `unignore` reopens a day the tracker closed as
  `ALL_RESOLVED` when the match is unresolved again, mirroring the recompute rule.
- **Settings.** `tt.pipeline.tracker.*` are tuning values with defaults (`PT1H`, `PT10M`, `PT30S`), also when the
  block is absent. CORS now allows `PUT` and `DELETE` for the new actions.
- **Dispatcher.** The worker catches `RuntimeException` as the task boundary (logged at ERROR) so an unexpected
  failure cannot stop later recomputes; the expected failures are logged at WARN.
- **Validation.** `mvn test` passes with the persistence and integration tests skipped: this machine has no Docker,
  so `MatchDayTrackerMigrationTest`, `JpaMatchDayRepositoryTest` and `TrackerRecomputeIntegrationTest` (and the V4
  migration itself) were written but not run against PostgreSQL. Run them with Docker before closing the feature.
- **Acceptance criteria check.** (1) recompute after every final run state and periodically: `TrackerRunObserver`,
  `TrackerRecomputeSchedule`, `MatchDayTrackerTest`; (2) status mapping, grace window and `reported_at`/run:
  `TrackerRulesTest`, `MatchTrackingTest`, `MatchDayTrackerTest`; (3) closing rule and several open days:
  `TrackerRulesTest`, `MatchDayTrackerTest`; (4) manual close, ignore and note with actor and time:
  `MatchDayActionsTest`, `MatchDaysApiWebTest`; (5) tests for window, postponed, closing rules and actions:
  `MatchDayWindowTest`, `MatchDayTrackerTest`, `TrackerRulesTest`, `MatchDayActionsTest`.
