# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>` and `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>` (tests mirror them under `src/test/java`). Steps 1-4 are core-only and build with JUnit/AssertJ; steps 5-10 add
the runtime adapters. No platform module, platform REST contract or `tt-league-ingest` code changes.

1. **Run vocabulary (`<core>/run/`).** Plain enums and values, no framework imports.
   - `PipelineSource` enum: `RFETM`, `BCNESA`, `FCTT` (mapping to ingest's lowercase codes is FEAT-00104's job).
   - `RunTrigger` enum: `SCHEDULED`, `MANUAL`, `RETRY`.
   - `RunStatus` enum: `QUEUED`, `RUNNING_INGEST`, `NO_CHANGES`, `PACKED`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`,
     `FAILED`, each with its allowed successors, plus `boolean canTransitionTo(RunStatus)`, `boolean isActive()` (exactly
     `QUEUED`, `RUNNING_INGEST`, `PACKED`, `IMPORTING`), `boolean isTerminal()` and `static Set<RunStatus> active()`.
     Transition table:

     | From | Allowed next |
     | --- | --- |
     | `QUEUED` | `RUNNING_INGEST`, `FAILED` |
     | `RUNNING_INGEST` | `NO_CHANGES`, `PACKED`, `FAILED` |
     | `PACKED` | `IMPORTING`, `FAILED` |
     | `IMPORTING` | `SUCCEEDED`, `PARTIAL`, `FAILED` |
     | `NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED` | — (terminal) |

     Step retries (FEAT-00104) add `pipeline_step` attempts and never change the run status, so there are no
     self-transitions.
   - `IllegalRunTransitionException extends IllegalStateException` with `from`, `to` and the run id in the message.
   - `RunError(String code, String message)` record: both non-blank; `code` at most 64 characters.
   - `ScopeFilter` record mirroring the ingest scope contract of FEAT-00098: nullable `category`, `group`, `phase`,
     `territory`, `gender` (non-blank when present) and `List<Integer> matchDays` (positive, copied, empty allowed);
     the constructor rejects a filter with no field set.
   - `RunScope` record with `List<ScopeFilter> filters`: an empty list means the whole season (`RunScope.fullSeason()`);
     duplicates are removed keeping first-seen order, as ingest does.

2. **`PipelineRun` aggregate (`<core>/run/PipelineRun.java`).** Immutable final class; every transition returns a new
   instance and checks `RunStatus.canTransitionTo` first.
   - Fields: `UUID id`, `PipelineSource source`, `String season` (`\d{4}-\d{4}`, second year = first + 1),
     `RunScope scope`, `RunTrigger trigger`, `String requestedBy` (non-blank, at most 128 characters; the scheduler
     will use `system:scheduler`), `UUID retryOfRunId` (required for `RETRY`, rejected otherwise), `RunStatus status`,
     `Instant createdAt`, `Instant startedAt`, `Instant finishedAt`, `String ingestRunId` (ingest's hex id, at most
     64 characters), `UUID importJobId`, `RunError error`, `long version`.
   - Factory `PipelineRun.queue(UUID id, source, season, scope, trigger, requestedBy, retryOfRunId, Instant now)`
     creates a `QUEUED` run with version `0`. `PipelineRun.restore(...)` (all fields) is used only by adapters and
     re-validates the invariants below.
   - Transitions (each takes `Instant at`): `startIngest(String ingestRunId)` → `RUNNING_INGEST`, sets `startedAt`;
     `noChanges()`, `packed()`; `startImport(UUID importJobId)` → `IMPORTING`; `succeed()`, `partial()`;
     `fail(RunError)` (from any active state). Entering a terminal state sets `finishedAt`.
   - Invariants checked in the constructor: `startedAt` absent for `QUEUED`, present for every other non-`FAILED`
     status and optional for `FAILED` (a queued run can fail before starting); `finishedAt` present iff terminal;
     `error` present iff `FAILED`; `importJobId` present for `IMPORTING`, `SUCCEEDED` and `PARTIAL`, absent before
     `IMPORTING`, optional for `FAILED`; timestamps not before `createdAt` and `finishedAt` not before `startedAt`.
   - `version` is carried for optimistic locking; the core never increments it (the adapter does).

3. **Step, artifact and report values (`<core>/run/`).**
   - `StepKind` enum `INGEST` (the ingest run: download, parse, package), `FETCH_PACKAGE` (ZIP download and SHA-256
     check), `IMPORT` (platform import job). `StepStatus` enum `RUNNING`, `SUCCEEDED`, `FAILED`.
   - `PipelineStep` immutable: `UUID id`, `UUID runId`, `StepKind kind`, `int attempt` (≥ 1), `StepStatus status`,
     `Instant startedAt`, `Instant finishedAt`, `String externalRef` (ingest run id or import job id), `String outcome`
     (the external outcome as received, e.g. ingest `NO_CHANGES` or import `PARTIAL`, at most 32 characters),
     `Boolean retryable`, `RunError error`, `String logRef` (at most 512 characters, filled by FEAT-00115). Factory
     `start(...)` and transitions `succeed(Instant, String outcome)`, `fail(Instant, String outcome, RunError, boolean
     retryable)`; only a `RUNNING` step can finish.
   - `ArtifactKind` enum `ZIP`, `MANIFEST`, `RAW`, `JSON`. `RunArtifact` record: `UUID id`, `UUID runId`, `kind`,
     `String storageKey` (non-blank, at most 512 characters, relative path; absolute paths and `..` segments rejected),
     `String sha256` (64 lowercase hex), `long sizeBytes` (≥ 0), `Instant createdAt`.
   - `ImportReport` record: `UUID runId`, `UUID importJobId`, `String importStatus` (`SUCCEEDED`, `PARTIAL` or
     `FAILED`, as the platform job reports it), counters copied from the platform's `ImportProcessResult` summed over
     the job's seasons: `filesSeen`, `itemsPersisted`, `skipped`, `processorFailures`, `scheduledCreated`,
     `upgradedToPlayed`, `rescheduled`, `partialActas`, `invalidActas`, `unresolvedPendingFixtures` (all ≥ 0),
     `List<String> issues` (copied), `String rawReport` (the platform job JSON as received, non-blank; the core treats
     it as opaque text), `Instant receivedAt`.

4. **Repository ports (`<core>/run/port/`).**
   - `PipelineRunRepository`: `PipelineRun create(PipelineRun run)` (throws `ActiveRunConflictException` when the
     source already has an active run); `PipelineRun update(PipelineRun run)` (persists a transition and returns the
     run with the incremented version; throws `StaleRunException` when the stored version differs and
     `IllegalArgumentException` for an unknown id); `Optional<PipelineRun> findById(UUID id)`;
     `Optional<PipelineRun> findActiveBySource(PipelineSource source)`; `List<PipelineRun> findByStatusIn(Set<RunStatus>
     statuses)` (oldest first; FEAT-00104 uses it to recover active runs after a restart).
   - `PipelineStepRepository`: `PipelineStep save(PipelineStep)` (insert or update by id; a second step with the same
     run, kind and attempt is rejected) and `List<PipelineStep> findByRunId(UUID)` ordered by `startedAt`, `attempt`.
   - `RunArtifactRepository`: `RunArtifact add(RunArtifact)`, `List<RunArtifact> findByRunId(UUID)`.
   - `ImportReportRepository`: `ImportReport add(ImportReport)` (a second report for the same run is rejected),
     `Optional<ImportReport> findByRunId(UUID)`.
   - Exceptions in `<core>/run/port/`: `ActiveRunConflictException` (carries the `PipelineSource`; message names the
     source), `StaleRunException` (run id, expected version). Both unchecked.

5. **Core tests (`tt-league-pipeline-orchestrator-core/src/test/java/.../core/run/`).**
   - `RunStatusTest`: parameterized over all 64 pairs; exactly the table's pairs are allowed; `active()` equals
     `{QUEUED, RUNNING_INGEST, PACKED, IMPORTING}`; terminal states allow nothing.
   - `PipelineRunTest`: factory validation (bad season, blank or long `requestedBy`, `RETRY` without
     `retryOfRunId`, `MANUAL` with one); the happy paths `QUEUED → RUNNING_INGEST → PACKED → IMPORTING → SUCCEEDED`,
     `→ PARTIAL`, `RUNNING_INGEST → NO_CHANGES`, `QUEUED → FAILED`; timestamps and error set correctly; every illegal
     transition throws `IllegalRunTransitionException`; `restore` rejects broken invariants (terminal without
     `finishedAt`, `FAILED` without error).
   - `PipelineStepTest`, `RunArtifactTest` (SHA-256 format, path rules, negative size), `ImportReportTest` (negative
     counter, blank raw report), `RunScopeTest` (empty filter rejected, de-duplication order, full season).
   - `CoreDependencyRulesTest` must keep passing unchanged.

6. **Runtime dependencies (`tt-league-pipeline-orchestrator-runtime/pom.xml` only).** Add
   `spring-boot-starter-data-jpa`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`,
   `org.postgresql:postgresql` (scope `runtime`); test scope `org.springframework.boot:spring-boot-testcontainers`,
   `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`. All versions come from the imported Spring
   Boot 3.5.8 BOM; no parent-POM change.

7. **Configuration (`src/main/resources/application.yml`).**
   - `spring.datasource.url: ${PIPELINE_DB_URL}`, `username: ${PIPELINE_DB_USERNAME}`, `password:
     ${PIPELINE_DB_PASSWORD}`, all without defaults (startup fails when one is missing, as for the existing
     `PIPELINE_*` variables).
   - `spring.flyway.schemas: pipeline`, `spring.flyway.default-schema: pipeline`, `spring.flyway.create-schemas: true`
     (the history table lives in `pipeline`), `spring.flyway.locations: classpath:db/migration`.
   - `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`,
     `spring.jpa.properties.hibernate.default_schema: pipeline`.

8. **Flyway migration (`src/main/resources/db/migration/V1__pipeline_run_model.sql`).** Everything schema-qualified
   with `pipeline.`; no reference to any platform table (D2). Enumerated columns are `varchar` with `CHECK (... IN
   (...))` listing the core enum values.
   - `pipeline_run`: `id uuid PK`, `source varchar(16) NOT NULL`, `season varchar(9) NOT NULL CHECK (season ~
     '^[0-9]{4}-[0-9]{4}$')`, `scope jsonb NOT NULL`, `trigger varchar(16) NOT NULL`, `requested_by varchar(128) NOT
     NULL`, `retry_of_run_id uuid NULL REFERENCES pipeline.pipeline_run(id)`, `status varchar(16) NOT NULL`,
     `created_at`, `started_at`, `finished_at` (`timestamptz`), `ingest_run_id varchar(64)`, `import_job_id uuid`,
     `error_code varchar(64)`, `error_message text`, `version bigint NOT NULL DEFAULT 0`;
     `CHECK ((trigger = 'RETRY') = (retry_of_run_id IS NOT NULL))`.
   - `CREATE UNIQUE INDEX ux_pipeline_run_active_source ON pipeline.pipeline_run (source) WHERE status IN ('QUEUED',
     'RUNNING_INGEST', 'PACKED', 'IMPORTING');` plus `ix_pipeline_run_source_created (source, created_at DESC)` and
     `ix_pipeline_run_status (status)`.
   - `pipeline_step`: `id uuid PK`, `run_id uuid NOT NULL REFERENCES pipeline.pipeline_run(id)`, `kind varchar(16)`,
     `attempt int NOT NULL CHECK (attempt >= 1)`, `status varchar(16)`, `started_at timestamptz NOT NULL`,
     `finished_at timestamptz`, `external_ref varchar(64)`, `outcome varchar(32)`, `retryable boolean`, `error_code
     varchar(64)`, `error_message text`, `log_ref varchar(512)`; `UNIQUE (run_id, kind, attempt)`.
   - `run_artifact`: `id uuid PK`, `run_id uuid NOT NULL REFERENCES pipeline.pipeline_run(id)`, `kind varchar(16)`,
     `storage_key varchar(512) NOT NULL`, `sha256 char(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$')`, `size_bytes
     bigint NOT NULL CHECK (size_bytes >= 0)`, `created_at timestamptz NOT NULL`; `UNIQUE (run_id, kind,
     storage_key)`.
   - `import_report`: `run_id uuid PK REFERENCES pipeline.pipeline_run(id)`, `import_job_id uuid NOT NULL`,
     `import_status varchar(16) NOT NULL`, the ten counters as `bigint NOT NULL CHECK (... >= 0)`, `issues jsonb NOT
     NULL`, `raw_report jsonb NOT NULL`, `received_at timestamptz NOT NULL`.
   - Foreign keys stay inside `pipeline` and use the default `NO ACTION` (runs are history and are not deleted here;
     retention in FEAT-00114 decides about artifact rows).

9. **JPA adapters (`<rt>/persistence/`).**
   - Entities `PipelineRunEntity`, `PipelineStepEntity`, `RunArtifactEntity`, `ImportReportEntity` (`@Table(schema =
     "pipeline", name = ...)`, `@Enumerated(EnumType.STRING)`, `@Version` on `PipelineRunEntity.version`, jsonb
     columns as `String` with `@JdbcTypeCode(SqlTypes.JSON)`). Spring Data interfaces `PipelineRunJpaRepository`
     (`findBySourceAndStatusIn`, `findByStatusInOrderByCreatedAtAsc`), `PipelineStepJpaRepository`
     (`findByRunIdOrderByStartedAtAscAttemptAsc`), `RunArtifactJpaRepository`, `ImportReportJpaRepository`.
   - `RunScopeJson` (Jackson `ObjectMapper` bean from Boot) writes `RunScope` as `{"scopes":[{...}]}` with the
     FEAT-00098 keys (`category`, `group`, `phase`, `territory`, `gender`, `matchDays`), omitting nulls, and reads it
     back strictly (unknown keys fail). `ImportReport.issues` uses the same mapper as a JSON array.
   - `JpaPipelineRunRepository implements PipelineRunRepository` (`@Repository`, `@Transactional`): `create` does
     `saveAndFlush` and translates a `DataIntegrityViolationException` whose cause is a SQL state `23505` on
     `ux_pipeline_run_active_source` into `ActiveRunConflictException`; any other integrity violation is rethrown
     unchanged. `update` loads the entity, throws `StaleRunException` when the stored version differs from
     `run.version()`, copies the mutable fields, flushes and maps the result back (new version). Mapping goes through
     `PipelineRun.restore`, so a corrupted row fails loudly.
   - `JpaPipelineStepRepository`, `JpaRunArtifactRepository`, `JpaImportReportRepository` implement the remaining
     ports; `ImportReportRepository.add` rejects an existing run id with `IllegalStateException` before inserting.
   - No controller, scheduler or HTTP client is added (FEAT-00104/105/106).

10. **Runtime tests (Testcontainers PostgreSQL, `postgres:16-alpine`).**
    - `src/test/java/<rt>/persistence/PostgresTestConfiguration.java`: `@TestConfiguration(proxyBeanMethods = false)`
      with a `@Bean @ServiceConnection PostgreSQLContainer<?>`. Shared `@SpringBootTest` base or meta-annotation
      `@PipelinePersistenceTest` sets the four `tt.pipeline.*` test properties, imports the configuration and is
      `@Testcontainers(disabledWithoutDocker = true)`; each test class truncates the four tables in `@BeforeEach`
      (`TRUNCATE ... CASCADE` through `JdbcTemplate`), so tests commit for real (no rollback-only `@DataJpaTest`,
      which would hide the unique-index behaviour).
    - `PipelineSchemaMigrationTest`: Flyway reports `V1` applied in schema `pipeline`; the four tables and
      `ux_pipeline_run_active_source` exist in `pipeline` and nothing was created in `public`; Hibernate `validate`
      passed (context loaded); direct JDBC inserts with an unknown status, a bad season, a bad SHA-256 and a `RETRY`
      without `retry_of_run_id` are rejected by the database.
    - `ActiveRunConstraintTest`: a second active run for the same source raises `ActiveRunConflictException`; a run
      for another source is accepted; after the first run reaches each terminal state (`NO_CHANGES`, `SUCCEEDED`,
      `PARTIAL`, `FAILED`) a new run is accepted; two raw JDBC inserts of active rows for one source violate the index
      (DB-level guarantee independent of the adapter); two threads creating a run for the same source at once end
      with exactly one stored run and one `ActiveRunConflictException`.
    - `JpaPipelineRunRepositoryTest`: round-trip of every field including scope JSON and `retryOfRunId`; each
      lifecycle path persisted step by step with the version incremented; an update with a stale version raises
      `StaleRunException`; `findActiveBySource` and `findByStatusIn` ordering.
    - `JpaPipelineStepRepositoryTest` (round-trip, update to finished, duplicate `(run, kind, attempt)` rejected,
      ordering), `JpaRunArtifactRepositoryTest`, `JpaImportReportRepositoryTest` (round-trip of counters, issues and
      raw JSON; second report for a run rejected).
    - `PipelineOrchestratorApplicationTest` imports `PostgresTestConfiguration` (the context now needs a datasource)
      and is also skipped without Docker. `PipelineOrchestratorPropertiesTest` stays unchanged (it only binds
      properties).

11. **Documentation and guidance.**
    - New `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`: schema `pipeline`, one section per
      table (columns, types, constraints, meaning), the active-run partial index and its status list, the run
      transition table, the "no foreign keys into platform tables" rule, and the migration history (`V1`). Linked
      from the module README.
    - `tt-league-pipeline-orchestrator-runtime/README.md`: `PIPELINE_DB_URL`, `PIPELINE_DB_USERNAME`,
      `PIPELINE_DB_PASSWORD` in the configuration table; Flyway owns schema `pipeline` and the history table; the
      persistence tests need Docker and are reported as skipped without it.
    - `tt-league-pipeline-orchestrator-runtime/AGENTS.md`: replace "Do not add Testcontainers, Flyway or Spring
      Security until ..." with: Flyway, JPA and Testcontainers are in place; Spring Security waits for FEAT-00105;
      schema changes only through new `V<n>__*.sql` migrations (never edit an applied one); `ddl-auto` stays
      `validate`; update `docs/pipeline-datamodel.md` with every migration; never reference platform tables.
    - `tt-league-pipeline-orchestrator-core/AGENTS.md` and README: drop "skeleton until FEAT-00103"; describe the
      `run` package (state machine, values, repository ports).
    - Root `AGENTS.md`: next to the `rfetm-datamodel.md` rule, add that
      `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md` is the orchestrator schema contract.

12. **Validation.** `mvn -pl tt-league-pipeline-orchestrator-core,tt-league-pipeline-orchestrator-runtime -am test`
    with Docker running (persistence tests must execute, not skip), then the full `mvn test` from the root. Review the
    diff for `target/` content and for any reference to platform tables or `tt-data-league-*` packages.

## Acceptance Criteria

- [ ] `tt-league-pipeline-orchestrator-core` models `PipelineRun` and `PipelineStep` with states `QUEUED`, `RUNNING_INGEST`, `NO_CHANGES`, `PACKED`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, triggers `SCHEDULED`/`MANUAL`/`RETRY` and `requestedBy`, and rejects illegal transitions
- [ ] Flyway migrations in `tt-league-pipeline-orchestrator-runtime` create schema `pipeline` with `pipeline_run`, `pipeline_step`, `run_artifact` and `import_report`
- [ ] A partial unique index guarantees at most one `QUEUED`/`RUNNING_*`/`PACKED`/`IMPORTING` run per source
- [ ] JPA adapters implement the core repository ports; Testcontainers PostgreSQL tests cover the migrations, the uniqueness rule and state persistence
- [ ] The module documents its tables in a datamodel document next to the module README

# Implementation Guidelines

- No foreign keys into platform tables (D2). Store platform ids (import job id, match UUIDs) as plain columns.
- The core stays framework-free: no Jackson, JPA annotations or `java.time.Clock` injection in `<core>`. Callers pass
  `Instant`s; JSON (de)serialization of scope, issues and the raw report lives in the runtime adapter.
- The database is the guarantee for "one active run per source"; the adapter only translates the violation. Do not
  replace it with a check-then-insert in Java or an application lock.
- Flyway owns schema `pipeline` (D8). Hibernate only validates (`ddl-auto: validate`); never let it create or alter
  tables. The platform's `ddl-auto: update` setup is untouched.
- Dependencies are added to the runtime POM only, with versions from the Spring Boot BOM. Spring Security, HTTP
  clients, controllers and the scheduler are out of scope.
- Out of scope: the run executor and gateways (FEAT-00104), runs API and SSE (FEAT-00105), scheduling and ShedLock
  tables (FEAT-00106), `match_day`/`match_tracking`/`poll_schedule` (FEAT-00107/00108), `source_fetch` and
  `daily_stats` (FEAT-00113), retention (FEAT-00114). Later tables arrive as new migrations, never as edits to `V1`.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Run lifecycle" and "Tracking data model" (`pipeline_run`, `pipeline_step`, `run_artifact`, `import_report`).

## Planning notes (2026-10-04)

- **`NO_CHANGES` is terminal.** The proposal lists `NO_CHANGES → SUCCEEDED`, but an automatic hop would erase the
  outcome that FEAT-00108 (back-off after 3 consecutive no-change runs) and FEAT-00113 (runs by outcome) need, and the
  acceptance criterion's active-status list already excludes `NO_CHANGES`. The plan keeps `NO_CHANGES` as a final
  state. Revisit only if a consumer needs a single "successful" status; it can use `{SUCCEEDED, NO_CHANGES}`.
- **`QUEUED → FAILED` and `PACKED → FAILED` are allowed** (not in the proposal table): a queued run may be abandoned
  (orchestrator restart, ingest refusing the run) and a fetched ZIP may fail its SHA-256 check before import starts.
- **Step kinds follow D5, not the proposal's `DOWNLOAD/PARSE/PACK/IMPORT`.** The orchestrator drives one ingest run
  covering download, parse and package, then fetches the ZIP and submits the import, so the steps are `INGEST`,
  `FETCH_PACKAGE` and `IMPORT`. Ingest's per-stage detail stays in the ingest run (and `log_ref`). The proposal's
  `exit_code` column is dropped: the ingest REST service reports `outcome`/`retryable`, not process exit codes.
- **`import_report` mirrors the platform counters** (`ImportProcessResult` + `ImportLifecycleCounters`) instead of the
  proposal's `created/reported/corrected/rescheduled`: `scheduledCreated` ≈ created, `upgradedToPlayed` ≈ reported,
  `rescheduled` = rescheduled. The platform has no "corrected after report" counter; the raw job JSON is kept so a
  later feature can add one without a data loss.
- **One active run per source includes `QUEUED`.** FEAT-00105's "configurable to queue instead" therefore cannot
  insert a second `QUEUED` row for a source; that feature must hold the request elsewhere or keep rejecting with 409.
  Flagged for FEAT-00105 planning.
- **`requestedBy`** is the user id from the JWT for `MANUAL`, `system:scheduler` for `SCHEDULED`, and the requesting
  user for `RETRY`; `retry_of_run_id` links a `RETRY` run to its original (FEAT-00114).
- **Database.** The runtime uses its own `PIPELINE_DB_*` variables. It may point at the platform database (schema
  `pipeline`, as the proposal suggests) or a separate one; a dedicated role limited to schema `pipeline` is
  recommended for FEAT-00116 deployment.
- **Docker for tests.** No Docker CLI was found on the planning machine, and the repository has no Testcontainers
  usage yet. Persistence tests use `@Testcontainers(disabledWithoutDocker = true)`, so `mvn test` stays green (tests
  reported as skipped) where Docker is unavailable; step 12 requires running them with Docker before `in-review`, and
  the implementation notes must say whether they executed or were skipped. Open: whether CI should fail instead of
  skip when Docker is missing.
- PostgreSQL image `postgres:16-alpine`; align with the platform's production version if it differs.

## Status notes

- 2026-10-04: plan approved by the user and marked `ready`, including the planning decisions above. The Docker/CI
  question stays open and does not block implementation.
- 2026-10-04: implementation finished; moving to `in-review`. Delivered: core `run` package (`RunStatus` state machine,
  `PipelineRun`, `PipelineStep`, `RunArtifact`, `ImportReport`, `RunScope`, repository ports); runtime Flyway `V1`,
  JPA entities and adapters, `RunScopeJson`, `PIPELINE_DB_*` configuration, `docs/pipeline-datamodel.md`, README and
  AGENTS updates. Validation: core 105 tests and `RunScopeJsonTest` pass.
- **Persistence tests were written but NOT executed.** The implementation machine has no Docker, so the 26 Testcontainers
  tests (`PipelineSchemaMigrationTest`, `ActiveRunConstraintTest`, `Jpa*RepositoryTest`,
  `PipelineOrchestratorApplicationTest`) were reported as skipped. The migration, Hibernate `validate` against it (notably
  `char(64)` and the `jsonb` String columns) and the unique-index behaviour are therefore unverified. Run
  `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` with Docker before moving to `done`.
- Root `mvn test` stops at `tt-data-league-import` (`BcnesaImportProcessorsTest`: missing fixture
  `acta_bcnesa_2026_published.json`), a module this feature does not touch; later reactor modules did not run.
- Decisions: `PipelineRun` transitions take an explicit `Instant at` as the last argument; a duplicate step or artifact
  surfaces as `DataIntegrityViolationException` from the unique key; `StaleRunException` is also raised when a
  concurrent update loses the optimistic lock.
- 2026-10-04: closed as `done` on the user's explicit request. Acceptance criteria were checked on that instruction; the Testcontainers persistence tests had still not been executed (no Docker on the implementation machine) and remain to be run where Docker is available.
