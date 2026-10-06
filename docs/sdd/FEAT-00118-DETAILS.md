# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>`, `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>` and `tt-league-pipeline-orchestrator-frontend/src` as `<fe>`. Test sources mirror them.

The plan has four parts:
- Steps 1-3: the `recentMatchDays` lookback in the core (the default policy for the last 3 past match days).
- Steps 4-7: runtime (configuration, persistence with one Flyway migration, policy DTOs, a read-only status endpoint).
- Steps 8-14: the frontend page.
- Steps 15-16: documentation and validation.

No new dependency. One Flyway migration (`V11`). Policies stay per source; season and competition only filter what is
shown.

**Contracts already in place (read from the code on 2026-10-06).**
- `PollingController` (`/api/pipeline/polling`): `GET /policies`, `GET /policies/{source}`, `PUT /policies/{source}`
  (ADMIN, full settings plus `version`), `DELETE /policies/{source}` (ADMIN), `GET /schedules?source=&season=`,
  `POST /schedules/{id}/resume` (`matches:write`). Errors are problem details with a `code`: `400`, `404`
  `POLL_SCHEDULE_NOT_FOUND`, `409` `STALE_POLICY`, `STALE_SCHEDULE`, `NOT_STOPPED`.
- `PollingSettings` (core record, nine values, validated in the canonical constructor) is built in exactly four
  production places: `PollingSettings.defaults()`, `PipelineOrchestratorProperties.Polling.Defaults.toSettings()`,
  `PollingPolicyRequest` (`<rt>/api/`) and `PollingSettingsJson.read` (`<rt>/persistence/`, strict: a missing or
  unknown key fails). Tests that build it: `PollingSettingsTest`, `PollingApiWebTest`, `JpaPollPolicyRepositoryTest`,
  `PipelineOrchestratorPropertiesTest`.
- `pipeline.poll_policy` (`V5`) holds one `settings` jsonb per source; `PollingSettingsProvider.effective` returns the
  stored override or the configured defaults. Policies are per source; the tick season and zone are the schedule
  ones.
- `AdaptivePollingTick.doTick` loads `OpenMatchDays.load(matchDays, source, season)` (OPEN days, and days closed as
  `ALL_RESOLVED` that still hold a postponed match; only days with candidate matches) and builds the units with
  `ScopeBuilder.build`. The same `OpenMatchDays.load` serves the manual `OPEN_MATCH_DAYS` resolver
  (`TrackerOpenMatchDayScopeResolver`).
- Frontend bindings and DTO types exist but no screen uses them: `<fe>/api/polling.ts` (bound as `api.polling` in
  `bindApi.ts`), `PollingPolicy`, `PollingPolicyRequest`, `PollSchedule`, `PolicyLevel` in `<fe>/api/types.ts`.
  Capabilities exist in `<fe>/auth/permissions.ts`: `edit-polling-policy` (role `ADMIN`) and `resume-schedules`
  (permission `matches:write`); `Can` and `requirementText` give the disabled state and tooltip.
- Gap: no endpoint says which sources are adaptively polled or on a cron, nor the configured season and zone. They
  live only in `PipelineOrchestratorProperties.Schedule` (`season`, `zone`, `sources` with crons) and
  `PipelineOrchestratorProperties.Polling` (`sources`, `tickInterval`).

**Lookback rule.** A *group* is a tracker match-day key without its round: `(competition, groupNumber, phase)` of the
source and season. Within each group the pollable days from `OpenMatchDays.load` are ordered by `round` descending and
only the first `recentMatchDays` are kept. Pollable days are past or current by construction (an OPEN day has started
or awaits results; an `ALL_RESOLVED` day holds a postponed match), so these are "the last N past match days". Older
pollable days are left to the weekly `FULL_REFRESH` unit. The lookback applies only to adaptive polling; the manual
`OPEN_MATCH_DAYS` trigger and the match-day refresh still use every open day.

1. **Core: the setting (`<core>/polling/PollingSettings.java`).**
   - Add `int recentMatchDays` as the tenth component (after `noChangeThreshold`), validated `>= 1` with the message
     `recentMatchDays must be at least 1`. `defaults()` uses `3`.
   - Update the class Javadoc. Update every constructor call listed above in the same change.
2. **Core: the rule (`<core>/polling/scope/RecentMatchDays.java`, new).**
   - `public static List<OpenMatchDay> limit(List<OpenMatchDay> open, int perGroup)`: pure, no clock, no I/O; keeps the
     input order of the kept days; `perGroup < 1` throws `IllegalArgumentException`.
   - `AdaptivePollingTick.doTick`: `open = RecentMatchDays.limit(OpenMatchDays.load(...), settings.recentMatchDays())`
     before the empty check and the scope build. Nothing else in the tick changes: units no longer built are deleted
     by `upsertUnits` as today, and a unit whose filter loses rounds is narrowed through `withFilter`.
   - `TrackerOpenMatchDayScopeResolver` and `MatchDayRefresh` are not changed.
3. **Core tests.**
   - `PollingSettingsTest`: `recentMatchDays` `0` rejected, `1` accepted, default `3`; update the existing helpers.
   - `RecentMatchDaysTest` (new): one group with 5 days keeps the 3 highest rounds; two groups are limited
     independently; a phase or group number difference makes another group; fewer days than the limit are all kept;
     input order preserved; `perGroup = 0` rejected.
   - `AdaptivePollingTickTest`: a group with 5 open days builds units whose filter lists only the 3 latest rounds; a
     unit whose only days fall outside the lookback is deleted; a policy with `recentMatchDays = 1` narrows further.
   - `TrackerOpenMatchDayScopeResolverTest`: one case pinning that the manual resolver still uses every open day.
4. **Runtime: configuration (`<rt>/config/PipelineOrchestratorProperties.java`, `application.yml`).**
   - `Polling.Defaults` gets `Integer recentMatchDays` (null takes the core default) and passes it to
     `PollingSettings`; an invalid value fails startup through the existing `polling.defaults is invalid: ...` path.
   - `application.yml`: `recent-match-days: ${PIPELINE_POLLING_RECENT_MATCH_DAYS:3}` under `polling.defaults`.
   - `PipelineOrchestratorPropertiesTest`: default `3`, override, `0` fails naming the setting.
5. **Runtime: persistence.**
   - `PollingSettingsJson`: write and strictly read `recentMatchDays` (add it to `KEYS`; missing fails as for the
     other keys).
   - `db/migration/V11__polling_recent_match_days.sql`:
     `UPDATE pipeline.poll_policy SET settings = settings || '{"recentMatchDays": 3}'::jsonb WHERE NOT settings ? 'recentMatchDays';`
     The stored `version`, `updated_by` and `updated_at` are not changed (the value equals the documented default).
   - `JpaPollPolicyRepositoryTest`: round trip with `recentMatchDays`; a row written as in `V5` (nine keys) is readable
     after the migration (Testcontainers, skipped without Docker).
6. **Runtime: policy DTOs (`<rt>/api/`).** `PollingPolicyDto` and `PollingPolicyRequest` get `recentMatchDays`
   (required in the request, like the other nine; a missing value answers `400`). OpenAPI field descriptions like
   the neighbours.
7. **Runtime: polling status endpoint (`<rt>/api/`).**
   - `PollingStatusDto(String season, String zone, Duration tickInterval, List<SourceModeDto> sources)` with
     `SourceModeDto(PipelineSource source, String mode, String cron)`. `mode` is `ADAPTIVE`, `CRON` or `NONE`; `cron`
     is set only for `CRON`. Every `PipelineSource` is listed, in enum order. `season` and `zone` are null when nothing
     is scheduled. Durations are ISO-8601 like the policy DTO.
   - `GET /api/pipeline/polling/status` in `PollingController`, built from the injected
     `PipelineOrchestratorProperties` (read only, nothing derived beyond the mode). Any valid token, like the other
     reads; covered by the existing `/api/pipeline/**` rule in `SecurityConfiguration` (check, no new matcher).
   - `PollingApiWebTest`: adaptive, cron and unscheduled sources in one answer; season/zone null when nothing is
     scheduled; `401` without a token; the policy `GET` returns `recentMatchDays`; a `PUT` without it answers `400`;
     a `PUT` with `recentMatchDays = 0` answers `400`.
8. **Frontend types and binding.** `<fe>/api/types.ts`: `recentMatchDays: number` on `PollingPolicy` and
   `PollingPolicyRequest`; new `PollingStatus`, `SourceMode`, `PollingMode`. `<fe>/api/polling.ts`:
   `getPollingStatus`. Extend `<fe>/api/endpoints.test.ts` with the new call.
9. **Route and navigation.** `<fe>/App.tsx`: lazy `PollingPage` at `polling`. `<fe>/layout/navigation.ts`: a
   "Polling" section (icon `@mui/icons-material/Schedule`, already in the icons package) between Runs and
   Statistics. Update the `App.test.tsx` route table.
10. **Hooks (`<fe>/polling/`, new folder).**
    - `usePollingOverview()`: loads `status` and `policies` once, exposes `reload`; abortable like `useStatistics`.
    - `usePollSchedules(source, season)`: loads `GET /schedules`, refetches on a `run` event of the selected source
      through the shared events context (no own connection, same pattern as `useMatchDayCalendar`), exposes `reload`.
    - URL filters `pollingFilters.ts` (`source`, `season`; season defaults to the status season, source to the first
      adaptive source, else the first source), mirroring `statisticsFilters.ts`.
11. **Formatting (`<fe>/polling/format.ts`).** ISO-8601 duration to a short label (`PT2H` → `2 h`, `P7D` → `7 d`,
    `PT90M` → `1 h 30 min`, `PT168H` → `7 d`) and back for the form; level labels for `PolicyLevel`;
    `recentMatchDays` as "Last N match days per group". No rule is derived in the browser: level, interval, next run
    and stop state are shown as sent.
12. **Page sections (`<fe>/pages/PollingPage.tsx`, components in `<fe>/polling/`).**
    - `PollingModeCard`: one row per source with its mode chip (`Adaptive` / `Cron <expr>` / `Not scheduled`), the
      season, zone and tick interval; an info alert when no source is adaptive, pointing to `PIPELINE_POLLING_SOURCES`.
    - `PolicyTable`: one column per source, one row per setting (ten), an `Overridden` chip with
      `updatedBy`/`updatedAt`, and per-source `Edit` and `Reset to defaults` buttons wrapped in
      `Can capability="edit-polling-policy" mode="disable"`. Reset is enabled only when `overridden`. A source that is
      not `ADAPTIVE` keeps its column and buttons, with the caption "Not used: source is not adaptively polled".
    - `ScheduleTable` for the filtered source and season: kind, unit filter (category/group/phase/territory/gender
      like `<fe>/runs/TriggerResultList.tsx`), level chip, interval, `consecutiveNoChange`, next run, last run, pending
      run (link to `/runs/:id`), stop state. Stopped rows are highlighted and get a `Resume` button wrapped in
      `Can capability="resume-schedules" mode="disable"`. A manual refresh button calls `reload`. Empty state explains
      that schedules appear after the first adaptive tick. A caption states the lookback of the selected source
      ("Only the last N match days of each group are polled; older ones are covered by the weekly full refresh"),
      using the server value.
13. **Dialogs.**
    - `PolicyEditDialog`: the ten fields prefilled from the effective policy, client-side checks that mirror
      `PollingSettings` (all durations positive; `matchDay ≤ dayAfter ≤ daysTwoToSeven ≤ open ≤ fullRefresh`;
      `overdue ≤ fullRefresh`; `overdueStopAfterDays ≥ 1`; `noChangeThreshold ≥ 1`; `recentMatchDays ≥ 1`) in a pure
      `<fe>/polling/policyForm.ts`, sends `PUT` with the loaded `version`. `400` shows the server detail; `409
      STALE_POLICY` keeps the input and offers "Reload current values". Success reloads the overview.
    - Reset and resume confirmations reuse the `<fe>/calendar/MatchDayActionDialog.tsx` pattern. Resume answers
      `404`, `409 NOT_STOPPED` and `409 STALE_SCHEDULE` are shown and the schedule list is refetched.
14. **Frontend tests (Vitest + RTL).** `policyForm.test.ts` (each ordering rule, `recentMatchDays ≥ 1` and the valid
    default), `format.test.ts`, `pollingFilters.test.ts`, `usePollSchedules.test.tsx` (refetch on a matching `run`
    event only), `PollingPage.test.tsx` (sections render from fixtures; ADMIN vs non-admin gating with the requirement
    tooltip; resume visible only for stopped rows; `409` answers; the not-adaptive caption), and extend
    `<fe>/test/renderApp.tsx` fixtures for `/api/pipeline/polling/*` and `<fe>/test/TestApiProvider.tsx` for
    `polling`.
15. **Documentation.**
    - Runtime README "Adaptive polling": the lookback rule (scope paragraph and the defaults list with
      `recent-match-days` / `PIPELINE_POLLING_RECENT_MATCH_DAYS`), and `GET /status` in the endpoints list.
    - `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`: `recentMatchDays` in the `poll_policy`
      `settings` keys, and the `V11` row in the migration table.
    - Core `AGENTS.md` polling package: `RecentMatchDays` is the only place that applies the lookback, used only by
      `AdaptivePollingTick`; the manual `OPEN_MATCH_DAYS` resolver and `MatchDayRefresh` never limit.
    - Frontend README: the Polling page, its permissions and that it shows no schedules while polling is off.
    - Frontend `AGENTS.md`: polling state goes through the `src/polling/` hooks; levels, intervals, the lookback and
      stop state come from the server and are never derived.
    - `deploy/.env.example` is not changed (the default applies); mention `PIPELINE_POLLING_RECENT_MATCH_DAYS` only in
      the README.
16. **Validation.** `mvn -pl tt-league-pipeline-orchestrator-core -am test`,
    `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` (with Docker for the persistence tests),
    `mvn -pl tt-league-pipeline-orchestrator-frontend -am test`, then the full `mvn test`.

# Implementation Guidelines
- `PollingPolicy` and `RecentMatchDays` in the core are the only places for polling rules. The UI never derives a
  level, interval, next run, stop decision or which match days are inside the lookback; the form validation only
  mirrors `PollingSettings` and never replaces the server's `400`.
- The lookback limits only what adaptive polling builds units from. It never changes the tracker, never closes or
  ignores a match day, and never affects the manual `OPEN_MATCH_DAYS` trigger or the match-day refresh.
- Policies stay per source (decision 2026-10-06): no season- or competition-scoped overrides, no change to the
  `poll_policy` key or the `PollPolicyRepository` port. "Per source and current season" means the per-source policy
  applied by the tick of the configured schedule season.
- `PollingSettingsJson` stays strict; the `V11` migration is what makes stored overrides readable, never a lenient
  read or a silent default.
- Permission checks mirror `SecurityConfiguration` (`ADMIN` for policy changes, `matches:write` for resume) and never
  replace the server checks.
- No new event type: schedules refetch on `run` events and the manual refresh button. Policies and status are loaded
  on page open and after a change.
- The status endpoint exposes configuration that is already in the README (modes, crons, season, zone, tick
  interval); it never exposes keys, URLs or lock settings.
- Out of scope: enabling or disabling polling for a source at runtime (configuration stays environment-driven),
  editing crons, a per-unit "Run now" button (decision 2026-10-06; the calendar's match-day refresh covers it), a
  per-unit history view, season- or competition-scoped policies, and polling alerts (FEAT-00112 already sends them).

# Notes
- 2026-10-06: Created after the FCTT `SCOPE_UNMATCHED` fix (`SourceVocabulary.Fctt` now mirrors the import's `tdm` →
  `tercera-nacional` alias and lowercase `g1` folders) so that FCTT can be polled adaptively. Plan written for review
  before implementation.
- Open questions for review (first draft):
  - Is the read-only `GET /polling/status` endpoint acceptable, or should the page only show policies and schedules
    (without saying which sources are polled)?
  - Should a source that is not adaptive still show its policy (editable but unused), or hide it?
  - Should the schedule table also offer "Run now" for a unit (a `GROUP` trigger with its filter), or stay read-only
    apart from resume?
- 2026-10-06 (plan rebuild): the registry gained the "default polling policy for the last 3 past match days"
  criterion and a description listing per-source/season/competition granularity. Decisions with the user:
  - The criterion is a policy setting `recentMatchDays` (default 3, configurable and overridable per source) that
    adaptive polling enforces per group. This adds a core change and the `V11` migration, replacing the first draft's
    "no Flyway migration, no core change".
  - Policies stay per source; season and competition only filter the schedules shown.
  - No "Run now" per unit; the schedule table only offers resume.
  - Resolved from the first draft: the status endpoint stays; a non-adaptive source keeps its (editable, unused)
    policy column with a caption.
  - "All nine settings" in the acceptance criteria became "all ten settings, including `recentMatchDays`".
- Risk to check during implementation: with one round per week, three rounds cover about three weeks, so an
  `OVERDUE` unit usually leaves the lookback before `overdueStopAfterDays` (21) stops it. That is intended (the
  weekly full refresh and the tracker's `MATCH_UNREPORTED` alert still cover it) but changes how often `STOPPED`
  alerts are raised; state it in the README.
- 2026-10-06: Plan approved by the user; status moved to `ready`.
- 2026-10-06: Implemented and moved to `in-review`. Validation: core `mvn -pl tt-league-pipeline-orchestrator-core -am test`
  green (679 tests); runtime tests green except the pre-existing, unrelated
  `PipelineOrchestratorPropertiesTest.failsWhenTheStatisticsZoneIsMissingBlankOrInvalid` (the test expects "is not a valid
  time zone", the code says "is not a valid IANA time zone id"; same at HEAD); frontend lint, typecheck and Vitest green.
  The Testcontainers persistence tests, including the new `PollingRecentMatchDaysMigrationTest` for `V11`, were skipped
  because Docker was not available: run them with Docker before closing. The full-reactor `mvn test` was started but its
  result was not confirmed when the feature was moved.
- Implementation decisions: `PollingController` reads the existing `PipelineOrchestratorProperties.Schedule` and `Polling`
  beans for `GET /status` (`PollingStatusDto`; season and zone are null when no source is scheduled). `RunsApiWebTest`
  mocks those two beans because it loads every controller. The policy form takes ISO-8601 text (with the readable value
  as helper text) instead of converting durations to numbers. `filterText` in `src/runs/format.ts` is now exported for
  the schedule table.
- 2026-10-06 (full build result): the full-reactor `mvn test` stopped at `tt-data-league-import`
  (`BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas`: "Missing test fixture
  acta_bcnesa_2026_published.json"), so the modules after it, including the three orchestrator ones, were skipped in that
  run. That module is not touched by this feature; the orchestrator core, runtime and frontend were validated with their own
  `-pl ... -am` runs (see the note above). The full reactor is not green until that fixture failure is resolved.
- 2026-10-06 (BCNESA scope fix during review): every BCNESA match-day refresh failed with `SCOPE_UNMATCHED` because
  BCNESA status rows carry the downloaded category folder (`RTT PREFERENT`, `RTB VETERANS 2aA`) while the import names
  competitions after the parser's kebab-case export folder (`rtt-preferent`, `rtb-veterans-2aa`). `SourceVocabulary.Bcnesa`
  now kebab-cases the category like the parser's `kebab_case` before the `BcnesaCompetitionNames` lookup; the poll-unit
  filter keeps the raw category. Pinned by `SourceVocabularyTest`; core `-pl ... -am test` green (680 tests).
- 2026-10-06: Closed as `done` on the user's explicit request. Open items carried over: the Testcontainers persistence
  tests (including `PollingRecentMatchDaysMigrationTest`) still need a run with Docker, and the pre-existing
  `PipelineOrchestratorPropertiesTest` time-zone message failure and the `tt-data-league-import` fixture failure keep the
  full reactor red.
