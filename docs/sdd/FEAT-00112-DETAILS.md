# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>` and `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>`. Test sources mirror them; core test fixtures live in `<core-test>/execution/testing` (published `test-jar`).
Steps 1-7 are core, 8-17 runtime, 18-19 documentation and validation. No platform module, platform REST contract,
`tt-league-ingest`, frontend, root POM or parent-POM plugin change. The only new dependency is
`spring-boot-starter-mail` in the runtime POM, version managed by the existing `spring-boot-dependencies` 3.5.8 BOM.

**Decisions (2026-10-05, user).** The channel is **SMTP e-mail**. The adaptive-polling alerts (`scopeStopped`,
`scopeUnmatched`, now WARN logs in `LoggingPollingAlerts`) **also go through the notifier**.

**Contracts used (read from the code on 2026-10-05).**
- `RunObserver.runChanged(PipelineRun)` fires on every run transition through `CompositeRunObserver`, which isolates
  observer failures. Terminal statuses: `NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED` (`RunStatus.isTerminal`).
- `PipelineRunRepository.find(RunQuery)` returns runs newest first (`createdAt` desc, then `id`); `RunQuery` filters by
  sources, statuses and a `createdAt` range, with `size` 1-100. That is enough for "the two newest terminal runs of a
  source" and "the newest successful run of a source". No new run-repository method is needed.
- `MatchDay`: `state` (`UPCOMING`/`OPEN`/`CLOSED`), `closeReason`, `closedAt`, `closedBy`, `openedAt`, `key`.
  `MatchTracking`: `status`, `matchDateTime`, `isIgnored()`, `isResolved()`, teams. `MatchDayRepository` has
  `findBySourceAndSeason`, `findMatches(ids)` and `findSourceSeasonsWithUnclosedDays`, but no read across seasons by
  state or close time.
- `MatchDayChangeListener` (runtime `events/`) has one implementation, `RunEventBroadcaster`. It is called by
  `TrackerRecomputeDispatcher` after a recompute with `hasChanges()` and by `MatchDaysController` after operator
  actions (close and reopen included).
- `AdaptivePollingTick` already deduplicates its alerts: `scopeStopped` is sent once per stop (`PollSchedule.alertedAt`),
  and `scopeUnmatched` once per distinct message and local day. `PollingConfiguration.pollingAlerts()` returns
  `LoggingPollingAlerts`.
- Background work follows one pattern: a private single-thread executor that is never an `Executor` bean, plus a
  `SmartLifecycle` fixed-delay schedule. Examples: `TrackerRecomputeDispatcher` and `TrackerRecomputeSchedule`.

**Alert rules (decided here).** Every condition has a kind and a stable `conditionKey`. A condition is *raised* when
it holds and has no active alert row. It is *cleared* when an active row's condition no longer holds. Clearing sends
nothing.

| Kind | Key | Raised when | Cleared when |
|---|---|---|---|
| `MATCH_DAY_CLOSED` | match-day id | day is `CLOSED` with `closedAt >= now - closedLookback`. The lookback stops a flood for days closed before the feature was deployed. Every close reason counts, and the message names it. | the day is no longer `CLOSED` (reopened) or no longer exists. A later close raises again. |
| `RUN_FAILURES` | source | the two newest terminal runs of the source are both `FAILED` | the newest terminal run is not `FAILED` |
| `MATCH_UNREPORTED` | match id | match in an `OPEN` day, not ignored, status `SCHEDULED`/`AWAITING_RESULT`/`OVERDUE`, `matchDateTime` set and `matchDateTime + unreportedAfter <= now` | the match is reported, postponed, ignored, removed, or its day is no longer `OPEN` |
| `NO_RECENT_SUCCESS` | source | the source has an `OPEN` day and `max(newest successful run finishedAt, earliest openedAt of its OPEN days) <= now - noSuccessWindow` | a successful run inside the window, or no `OPEN` day left |

"Successful" means `SUCCEEDED` or `NO_CHANGES`. `PARTIAL` is not a success, and it also breaks a failure streak
because it is not `FAILED`. The window starts at `openedAt`, so a day that has just opened does not raise the alert
immediately.

**Delivery.** One evaluation pass sends **one e-mail** with every alert raised in that pass, plus earlier alerts whose
send failed (`notifiedAt` null). If the send succeeds, they are all marked notified. If it fails, nothing is marked,
the failure is logged, and the next pass retries. Polling alerts are sent at once as their own e-mail, without an alert
row and without a retry, because the tick already deduplicates them.

1. **Notifier port** (`<core>/alert/port`). `Notifier` has one method, `void send(Notification notification)`, and
   throws `NotificationException` (unchecked; carries only a short reason, never server credentials).
   `Notification(String subject, String body)` is a record of plain text with validated non-blank values; the subject
   is at most 200 characters with no line breaks. Javadoc: implementations may block; callers send off the run,
   tracker and request threads.
2. **Alert model** (`<core>/alert`).
   - `AlertKind` enum: `MATCH_DAY_CLOSED`, `RUN_FAILURES`, `MATCH_UNREPORTED`, `NO_RECENT_SUCCESS`.
   - `AlertCondition(kind, conditionKey, source, season, title, detail)` is one holding condition. `source` and
     `season` are nullable for future kinds, `title` is at most 255 characters and `detail` is multi-line text.
   - `Alert` is an immutable aggregate with `id`, `kind`, `conditionKey`, `source`, `season`, `title`, `detail`,
     `raisedAt`, `notifiedAt`, `notifyAttempts`, `lastFailure` (an exception simple name, at most 128 characters),
     `clearedAt` and `version`. It has `raise(condition, now)`, `restore(...)`, `notified(now)`,
     `notifyFailed(reason)` and `cleared(now)`, and each one returns a new instance. A cleared alert cannot be
     notified or cleared again (`IllegalStateException`).
   - `AlertSettings(Duration unreportedAfter, Duration noSuccessWindow, Duration closedLookback)` holds positive
     durations.
   - Validation helpers reuse the `TrackerChecks` style (package-private copy `AlertChecks`, matching `Checks`/
     `TrackerChecks`).
3. **Repository ports.**
   - `<core>/alert/port/AlertRepository`:
     - `List<Alert> findActive()` returns uncleared alerts, oldest first.
     - `Alert raise(Alert)` inserts a row. It throws `ActiveAlertExistsException` when an uncleared alert with the
       same kind and key exists, which is the multi-writer guard.
     - `Alert update(Alert)` uses a version check and throws `StaleAlertException`.
   - `<core>/tracker/port/MatchDayRepository` gets two read methods:
     - `List<MatchDay> findByState(MatchDayState state)` reads all sources and seasons.
     - `List<MatchDay> findClosedSince(Instant since)` reads all sources and seasons where `closedAt >= since`.
     - Javadoc for both: read-only, used by the alerts.
4. **AlertRules** (`<core>/alert/AlertRules`). A pure function with no I/O and no clock:
   `Set<AlertCondition> holding(AlertFacts facts, AlertSettings settings, Instant now)`. It applies the rules table
   above. `AlertFacts` is a record of what the evaluator read:
   - `openDays` and `recentlyClosedDays`
   - `openMatches`, the matches of the open days
   - `activeAlertDays`, the current state of match days referenced by active `MATCH_DAY_CLOSED` alerts, looked up by
     id
   - `newestTerminalRuns`, per source, up to two runs
   - `newestSuccess`, per source, as an optional `finishedAt`
   An active `MATCH_DAY_CLOSED` alert keeps holding past the lookback while its day (from `activeAlertDays`) is still
   `CLOSED`, because only a reopen clears it. The title and detail text are built here (`AlertTexts`):
   - source, season, competition, group, phase and round
   - match teams and date in UTC ISO form
   - run ids and `RunError.code`, never `RunError.message`
   - close reason and actor
   No URL, key or token.
5. **AlertEvaluator** (`<core>/alert/AlertEvaluator`). The constructor takes `AlertRepository`, `MatchDayRepository`,
   `PipelineRunRepository`, `Notifier`, `RunClock` and `AlertSettings`. `EvaluationOutcome evaluate()` does the
   following:
   1. Read the facts:
      - `findByState(OPEN)` and `findMatches` of those days
      - `findClosedSince(now - closedLookback)`
      - `findById` for match days of active `MATCH_DAY_CLOSED` alerts that are not in either list
      - per `PipelineSource`, `find(RunQuery(source, terminal statuses, page 0, size 2))` and
        `find(RunQuery(source, {SUCCEEDED, NO_CHANGES}, page 0, size 1))`
   2. Compute `holding` and compare it with `findActive()`.
   3. Clear active alerts whose `(kind, key)` no longer holds, using `update(cleared)`. A `StaleAlertException` skips
      that alert, and the next pass repairs it.
   4. Raise new conditions. An `ActiveAlertExistsException` means another writer won, so skip the condition and do not
      send it.
   5. Collect the alerts to send: those just raised plus active ones with `notifiedAt == null`. If there are none,
      stop. Otherwise send one `Notification`:
      - The subject is the title for a single alert, else `"<n> pipeline alerts"`.
      - The body has one section per alert, ordered by kind, then `raisedAt`.
   6. If the send succeeds, mark each alert `notified(now)`. If it throws `NotificationException`, mark each one
      `notifyFailed(reason)`, log WARN, and do not rethrow.
   `EvaluationOutcome(raised, cleared, sent, failed)` is returned for logging and tests. Gateway or repository
   failures propagate, and the runtime dispatcher logs and drops the pass. Nothing is written for conditions not
   reached.
6. **Triggers in the core.**
   - `AlertRequests` port (`<core>/alert/port`) has `void request()`. Implementations coalesce requests and never
     block or throw.
   - `AlertRunObserver implements RunObserver` calls `request()` only when `run.status().isTerminal()`.
   - `NotifyingPollingAlerts implements PollingAlerts` wraps a delegate (`LoggingPollingAlerts` in the runtime) and a
     `Consumer<Notification>` sink. It calls the delegate first, then hands a `Notification` to the sink:
     - `scopeStopped`: source, season, `scopeKey`, `stopReason`, `stoppedAt`, and "resume through the polling API".
     - `scopeUnmatched`: source, season and message.
     Any `RuntimeException` from the sink is logged and swallowed, as the `PollingAlerts` contract requires.
7. **Core fixtures and tests** (`<core-test>`).
   - Fixtures in `execution/testing`:
     - `InMemoryAlertRepository`, with the same unique and version rules
     - `RecordingNotifier`, the fake notifier: it records notifications and can be switched to throw
       `NotificationException`
     - `RecordingAlertRequests`
   - Extend `InMemoryMatchDayRepository` with `findByState` and `findClosedSince`.
   - Tests:
     - `AlertRulesTest`: each rule's raise and clear edges.
       - Lookback boundary; reopen clears; re-close raises.
       - `PARTIAL` breaks the streak; a single `FAILED` does not raise.
       - The unreported threshold is exact; ignored and postponed matches are excluded.
       - `NO_RECENT_SUCCESS` counts the window from `openedAt` and clears when no open day is left.
     - `AlertEvaluatorTest`:
       - once per condition across passes
       - no send when nothing new
       - one e-mail grouping several alerts
       - a send failure keeps `notifiedAt` null and the next pass resends
       - a conflict on raise is skipped without sending
       - clearing then re-raising sends again
       - texts contain no `RunError.message`
     - `AlertTest`: transitions and invariants.
     - `AlertRunObserverTest`.
     - `NotifyingPollingAlertsTest`: the delegate is called, and sink failures are swallowed.
     - `CoreDependencyRulesTest` must stay green.
8. **Flyway `V7__alerts.sql`** creates table `pipeline.alert`:
   - `id uuid PK`
   - `kind varchar(32) NOT NULL` with a CHECK on the four kinds
   - `condition_key varchar(255) NOT NULL`
   - `source varchar(16)` with a CHECK for the three sources, nullable
   - `season varchar(16)` nullable
   - `title varchar(255) NOT NULL`
   - `detail text NOT NULL`
   - `raised_at timestamptz NOT NULL`
   - `notified_at timestamptz` nullable
   - `notify_attempts integer NOT NULL DEFAULT 0` with a CHECK `>= 0`
   - `last_failure varchar(128)` nullable
   - `cleared_at timestamptz` nullable
   - `version bigint NOT NULL`
   It has a partial unique index `ux_alert_active (kind, condition_key) WHERE cleared_at IS NULL` and the index
   `ix_alert_raised (raised_at)`. There is no foreign key, because the match-day and match ids live only inside
   `condition_key`. No change to earlier migrations.
9. **JPA adapter.**
   - Add `<rt>/persistence/AlertEntity`, `AlertJpaRepository` and `JpaAlertRepository implements AlertRepository`.
   - `JpaAlertRepository` translates the unique-index violation on insert into `ActiveAlertExistsException` (match the
     constraint name, as `JpaPipelineRunRepository` does for the active-run index) and a version mismatch into
     `StaleAlertException`.
   - Add `findByState` and `findClosedSince` to `JpaMatchDayRepository` and `MatchDayJpaRepository` as derived
     queries on `state` and `closedAt`.
10. **Configuration** (`PipelineOrchestratorProperties`). Add an optional `Notifications notifications` component,
    `null` -> `Notifications.disabled()`, with these fields:
    - `Mail mail`, with `host`, `port`, `username`, `password`, `starttls`, `from`, `to` (a list) and
      `subjectPrefix`.
    - `evaluateInterval`, `unreportedAfter`, `noSuccessWindow` and `closedLookback`.
    Rules:
    - Enabled exactly when `mail.host` is non-blank.
    - When enabled:
      - `from` and at least one `to` are required and are parsed as addresses (`jakarta.mail.internet.InternetAddress`
        with strict parsing).
      - `port` is 1-65535.
      - `username` and `password` must both be set or both be blank.
      - Every duration must be positive.
    - When disabled, the other values are ignored.
    - `alertSettings()` maps to the core `AlertSettings`.
    - `toString()` of `Mail` masks the password, because records print every component. A test asserts it.
11. **`application.yml` and POM.**
    - Add `spring-boot-starter-mail` to the runtime POM.
    - Under `tt.pipeline.notifications` in `application.yml`, add these settings:

      | Setting | Variable | Default |
      |---|---|---|
      | mail host | `PIPELINE_MAIL_HOST` | empty (notifications off) |
      | mail port | `PIPELINE_MAIL_PORT` | `587` |
      | mail username | `PIPELINE_MAIL_USERNAME` | empty |
      | mail password | `PIPELINE_MAIL_PASSWORD` | empty |
      | mail STARTTLS | `PIPELINE_MAIL_STARTTLS` | `true` |
      | mail from | `PIPELINE_MAIL_FROM` | empty |
      | mail to | `PIPELINE_MAIL_TO` | empty (comma-separated list) |
      | subject prefix | `PIPELINE_MAIL_SUBJECT_PREFIX` | `[tt-pipeline]` |
      | evaluate interval | `PIPELINE_ALERTS_EVALUATE_INTERVAL` | `PT15M` |
      | unreported after | `PIPELINE_ALERTS_UNREPORTED_AFTER` | `PT48H` |
      | no-success window | `PIPELINE_ALERTS_NO_SUCCESS_WINDOW` | `PT24H` |
      | closed lookback | `PIPELINE_ALERTS_CLOSED_LOOKBACK` | `P1D` |

    - Do **not** use `spring.mail.*`. Those properties would auto-configure a `JavaMailSender` bean and Boot's mail
      health indicator, so `/actuator/health` would go DOWN whenever SMTP is unreachable.
12. **MailNotifier** (`<rt>/notification/MailNotifier implements Notifier`).
    - It builds a private `JavaMailSenderImpl` from `Mail`, which is not a bean. Settings:
      - host, port, username and password
      - `mail.smtp.auth` when a username is set
      - `mail.smtp.starttls.enable` and `mail.smtp.starttls.required` from `starttls`
      - `mail.smtp.connectiontimeout`, `timeout` and `writetimeout` at 10 s, 30 s and 30 s. These are constants
        documented in the README.
    - `send` creates a `SimpleMailMessage` (from, to, subject = prefix + " " + subject, text = body + footer naming
      the orchestrator).
    - It maps `MailException` to `NotificationException` carrying only the exception simple name and the SMTP reply
      code when available. It never includes the message text, host credentials or the body.
    - It logs INFO `notification sent: <subject>` (recipients count only) and never logs the password.
    - A package-private constructor takes a `JavaMailSender` for tests.
13. **AlertDispatcher** (`<rt>/notification/AlertDispatcher implements AlertRequests, SmartLifecycle`).
    - It owns one private single-thread executor (`pipeline-alerts`), never an `Executor` bean.
    - `request()` coalesces with an `AtomicBoolean pending`, so at most one queued pass. It enqueues
      `evaluator.evaluate()` and never throws; requests after `stop()` are logged and dropped.
    - `send(Notification)` enqueues a direct `notifier.send` and is used by the polling-alerts sink.
    - Each pass logs the `EvaluationOutcome` at INFO when it raised, cleared or sent something. It catches
      `GatewayException`/repository runtime failures at the task boundary like `TrackerRecomputeDispatcher` does: it
      logs a WARN, or an ERROR for an unexpected failure, and drops the pass.
    - When notifications are disabled, it is built without an evaluator or notifier. `request()` and `send()` are then
      no-ops, and startup logs one INFO line, `notifications disabled (PIPELINE_MAIL_HOST is not set)`.
    - It has `awaitIdle()` for tests.
14. **AlertEvaluationSchedule** (`<rt>/notification/AlertEvaluationSchedule implements SmartLifecycle`).
    - It is a private `ThreadPoolTaskScheduler` (not a bean) that calls `dispatcher.request()` every
      `evaluateInterval`, with fixed delay and the first tick after one interval. It is started only when
      notifications are enabled.
    - No ShedLock: the partial unique index makes raising idempotent across instances, and D9 deploys one instance.
    - Record in the README that with two instances a retried failed send can go out twice.
15. **Wiring** (`<rt>/notification/NotificationConfiguration`).
    - Bean `AlertRepository`, which is `JpaAlertRepository`; it can also live in the persistence configuration if
      repositories are declared there.
    - Bean `AlertDispatcher`, enabled or disabled from the properties. The `AlertEvaluator`, `MailNotifier` and
      `AlertSettings` are created inside the configuration method when enabled, not exposed as beans.
    - Bean `AlertRunObserver(dispatcher)`.
    - Bean `AlertEvaluationSchedule`.
    - Changes to existing wiring:
      - `RunExecutionConfiguration.runObserver` adds `AlertRunObserver` to the composite, injected by concrete type
        like the others.
      - `PollingConfiguration.pollingAlerts` returns `new NotifyingPollingAlerts(new LoggingPollingAlerts(),
        dispatcher::send)` when notifications are enabled, else `LoggingPollingAlerts`. Update the
        `LoggingPollingAlerts` javadoc, which no longer says "until FEAT-00112".
      - `MatchDayChangeListener`: add a `@Primary` composite bean in `RunEventsConfiguration`,
        `CompositeMatchDayChangeListener(broadcaster, alerts)`, in `<rt>/events`. Each part is isolated with a logged
        catch, as `TrackerRecomputeDispatcher.notifyChanges` does. For `RECOMPUTED` and `ACTION` causes it calls
        `RunEventBroadcaster` and then `dispatcher.request()`, so a manual close or reopen and a tracker close are
        evaluated promptly. `TrackerConfiguration` and `MatchDaysController` stay unchanged because they inject the
        interface.
16. **Runtime tests.**
    - `PipelineOrchestratorPropertiesTest`:
      - disabled by default
      - enabled requires `from` and `to`
      - bad address, port, username/password pairing and non-positive durations fail with the setting named
      - `Mail.toString()` masks the password
    - `MailNotifierTest`, with a capturing `JavaMailSender` stub (Mockito is already on the test classpath):
      - message fields and prefix
      - a `MailSendException` becomes a `NotificationException` without its text
      - the password never appears in logs or exceptions
    - `AlertDispatcherTest`:
      - request coalescing
      - a disabled dispatcher does nothing
      - a failing pass does not stop the next one
      - a direct send failure is logged only
    - `CompositeMatchDayChangeListenerTest`.
    - `AlertsMigrationTest` (Testcontainers): table, CHECKs, partial unique index; a second active row fails and a row
      after clearing succeeds.
    - `JpaAlertRepositoryTest`: raise conflict, version conflict, `findActive` order.
    - `JpaMatchDayRepositoryTest`: cases for `findByState` and `findClosedSince`.
    - `PipelineOrchestratorApplicationTest` (context loads) with notifications disabled. Add one context test with a
      mail host set and assert the dispatcher is enabled, no `JavaMailSender` bean exists and the health endpoint
      does not include a mail component.
    - `TrackerRecomputeIntegrationTest` or a new `AlertsIntegrationTest`:
      - Two failing runs through the HTTP stubs end in exactly one `RecordingNotifier` notification.
      - A third failure sends nothing.
      - A success clears the alert.
17. **Logging and secrets review.** Grep the new code for `password`, `apiKey` and `getMessage()` in notifier or alert
    texts. Error texts use `RunError.code` only. Polling alert texts already carry only user-facing messages.
18. **Documentation.**
    - `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`: an `## alert` section (columns, partial
      unique index, written only by `AlertEvaluator` through `JpaAlertRepository`, no FKs) and a `V7` row in the
      migration history.
    - Runtime `README.md`:
      - a `## Notifications` section: channel, enable/disable rule, each variable with its default, the four alert
        rules and the polling alerts, once-per-condition and retry behaviour, the one-e-mail-per-pass grouping,
        timeouts and the two-instance caveat
      - the new variables in the configuration tables
    - `tt-league-pipeline-orchestrator-core/AGENTS.md`: an "Alert package" paragraph. `AlertRules` is the only place
      that decides alert conditions. The evaluator is the only writer of alerts. The `Notifier` and `AlertRequests`
      implementations never throw into callers. List the new test-jar fixtures.
    - `tt-league-pipeline-orchestrator-runtime/AGENTS.md`:
      - Notifications go only through `AlertDispatcher`, with a private executor and no `Executor` bean.
      - No `spring.mail.*` and no `JavaMailSender` bean.
      - Never log SMTP credentials or put them in messages.
19. **Validation.** Run these:
    - `mvn -pl tt-league-pipeline-orchestrator-core -am test`
    - `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`, with Docker running for the Testcontainers tests
    - the full `mvn test`

    Then review the diff for secrets, `target/` content and changes outside scope.

## Acceptance Criteria

- [x] A `Notifier` port has one SMTP e-mail adapter, configured from the environment (`PIPELINE_MAIL_*`) and disabled when `PIPELINE_MAIL_HOST` is unset
- [x] Alerts fire for: match day closed, two consecutive failed runs for a source, a match unreported past a configured threshold, and no successful run in 24 h during an open match day
- [x] Each alert is sent once per condition until it clears; tests use a fake notifier
- [x] The adaptive-polling alerts (scope stopped, scope unmatched) are also sent through the notifier, keeping their WARN log lines

# Implementation Guidelines

- Never include credentials, tokens, API keys, SMTP passwords or `RunError.message` in e-mails, logs, alert rows or
  exceptions. Error texts use `RunError.code`, and mail failures keep only the exception simple name and SMTP code.
- The core stays framework-free. `Notifier`, `AlertRequests` and `AlertRepository` are core ports, and Jakarta Mail
  and Spring Mail appear only in the runtime. `CoreDependencyRulesTest` must pass.
- `AlertRules` is the single decision point for conditions. The evaluator, the dispatcher and the UI never re-derive
  them. Platform match state still comes only from the tracker's stored data; the alerts never call the platform.
- Alerts are side channels. They never block or fail a run, a recompute, a polling tick or an HTTP request. Every send
  happens on the `AlertDispatcher` thread.
- Do not configure `spring.mail.*` and do not expose a `JavaMailSender` bean (mail health indicator). Do not add
  `@EnableScheduling`, ShedLock or a scheduler bean for alerts.
- Notifications are opt-in. A blank host disables them with no fallback channel. A set host with invalid companion
  settings fails startup with the setting named.
- Schema changes only through `V7__alerts.sql`, with the datamodel document updated in the same change. No foreign
  keys to tracker or run tables, because alert keys are snapshots.
- Out of scope:
  - "resolved" e-mails when an alert clears
  - an alerts REST API or UI view (candidate for FEAT-00113)
  - per-recipient or per-source routing
  - other channels (Telegram, Slack, webhook)
  - HTML e-mails
  - digests over time windows
  - alert acknowledgement
  - changing the tick's existing polling-alert deduplication

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Notifications" and "Operational observability" alerts. Blocked on the channel decision.

- 2026-10-05: Channel decided by the user: **SMTP e-mail** (resolves the FEAT-00096 open question "Notification
  channel for alerts"). The user also decided to route the existing adaptive-polling alerts through the notifier. That
  adds an acceptance criterion and names the channel in the first one. Effort raised to large: a new core package, a
  Flyway migration, the JPA adapter, the mail adapter and the wiring.
- 2026-10-05 planning decisions:
  - "Successful run" means `SUCCEEDED` or `NO_CHANGES`, and `PARTIAL` breaks a failure streak.
  - The no-success window starts at the latest of the newest success and the earliest `openedAt` of the open days.
  - Match-day-closed alerts look back `P1D` so existing closed days do not flood on deployment.
  - The unreported threshold is counted from `matchDateTime`, default `PT48H` (below the platform's 7-day grace
    period).
  - One e-mail per evaluation pass.
- 2026-10-05: Plan approved by the user; status set to `ready`.
- Open: confirm the default thresholds (`PT48H` unreported, `PT15M` evaluation interval) with the operator once real
  match days have run.
- 2026-10-05: Implemented. Validation: `mvn -pl tt-league-pipeline-orchestrator-core -am test` and
  `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` pass. The Testcontainers tests (116 skipped, including
  `AlertsMigrationTest`, `JpaAlertRepositoryTest`, the new `JpaMatchDayRepositoryTest` cases and
  `NotificationsContextTest`) were NOT run because Docker was unavailable; run them before closing. Deviation: the
  end-to-end alert test (`AlertsIntegrationTest`) drives the real run observer and dispatcher over in-memory stores
  instead of HTTP stubs, because the notifier is not a bean. `Alert.raise` takes the id as a parameter. The full
  `mvn test` stops at an existing failure in `tt-data-league-import` (`BcnesaImportProcessorsTest`, missing untracked
  fixture `acta_bcnesa_2026_published.json`), unrelated to this feature.
