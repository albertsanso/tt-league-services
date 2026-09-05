# Build Plan

1. **Establish a like-for-like performance and behavior baseline before refactoring.**
   - Use the same source, ACTAS folder, explicit season, restored database snapshot, JVM settings,
     and consolidation settings for the CLI and
     `POST /api/v1/administration/import/start?importResourceId=<uuid>`. The API resource is always
     season-scoped, so the comparison CLI command must include the matching `--season`.
   - Run one warm-up and at least three measured executions for each supported source (RFETM, BCNESA,
     and FCTT). Restore the database snapshot before every measured run so idempotent re-imports do
     not hide write costs.
   - Record wall-clock duration, files/fixtures seen and dispatched, processor failures, SQL statement
     counts, and club/player/match writes. Measure shared-executor time separately from HTTP transport
     time.
   - Store the commands, fixture/database identifiers (without credentials), raw measurements, median,
     and API-to-CLI ratio in `# Notes`. The acceptance target is an API shared-executor median no more
     than 10% slower than the CLI median for the same inputs. If the baseline disproves the recording
     decorators as a material cause, retain the shared-execution refactor but optimize only measured
     database/query hot spots.

2. **Introduce one execution contract shared by both adapters.**
   - Add `shared/execution/ImportExecutionService`, `ImportExecutionRequest`,
     `ImportExecutionOptions`, `ImportExecutionResult`, `ImportExecutionMetrics`,
     `ImportExecutionIssue`, and `PostProcessingOutcome` under
     `tt-data-league-import/src/main/java/org/cttelsamicsterrassa/data/load/`.
   - The request carries `ImportSource`, the ACTAS `Path`, and an optional `Season`. Options carry
     independently enabled club/player consolidation modes, an optional server-resolved RFETM teams
     folder, and the bounded persistence batch size. Defaults disable consolidation and use the
     configured batch size.
   - Normalize RFETM/FCTT and BCNESA traversal summaries into one result: source, selected season,
     status, elapsed duration, files seen, items seen/dispatched, skipped/unresolved items, processor
     failures, structured issues, persistence counters, and ordered post-processing outcomes.
   - Keep `ImportResourceProcessService` in `tt-data-league-core-domain` as the inward-facing API
     application port. `NavigatorBackedImportResourceProcessService` remains its `@Primary` adapter,
     but becomes a thin mapper from `ImportResource` to the shared execution request and from the
     shared result to `ImportProcessResult`; it must no longer construct recording decorators or
     duplicate source dispatch.
   - Extend `ImportProcessResult`, `ImportProcessResultDto`, and
     `ImportProcessResultDtoMapper` only with backward-compatible execution fields. Preserve the
     current `status`, `filesSeen`, `itemsPersisted`, `skipped`, and `processorFailures` JSON fields;
     document that the legacy `itemsPersisted` value is the navigator dispatch count until exact
     persistence counters are available.

3. **Move source dispatch, traversal, failure policy, and processor ordering into the shared executor.**
   - Implement `NavigatorImportExecutionService` in the new execution package and inject the existing
     RFETM, BCNESA, and FCTT navigators plus their Spring-ordered processor lists. Preserve the current
     `@Order` contract: Team `10`, Player `20`, Match `30`.
   - When the request has no season, call each navigator's `traverse(...)` overload; when it has a
     season, call `traverseSeason(...)`. This preserves CLI all-season behavior while keeping API
     imports scoped to the selected resource season.
   - Add a source-neutral structured traversal issue record and include issues in
     `TraversalSummary` and `BcnesaTraversalSummary`. Populate it in the navigators' existing
     per-processor exception boundary with processor, file/fixture location, and safe error message.
     Continue isolating a failed processor so peer processors see the item.
   - Remove `RfetmProcessRecordingProcessor`, `BcnesaProcessRecordingProcessor`, and
     `FcttProcessRecordingProcessor` after the API adapter consumes navigator issues directly. Keep
     exceptions visible through the normalized result and logs; do not add broad catches or turn
     processor failures into success.
   - Define overall success once: traversal I/O/configuration succeeded, `processorFailures == 0`, all
     buffered writes flushed successfully, and every requested post-processing phase succeeded.
     Empty traversal remains `EMPTY_RESULT`; any execution or requested post-processing failure is
     `FAILURE`.

4. **Add execution-local lookup caches without changing identity rules.**
   - Create a new `ImportRunContext` for every `execute(...)` call. Pass it through new navigator
     overloads into `MatchReportContext`, `BcnesaMatchReportContext`, and
     `FcttMatchReportContext`; preserve the existing public navigator overloads by having them create
     a fresh default context.
   - Cache teams by exact `(source, season, name)`, player seasons by exact
     `(source, season, licence)`, and matches by their complete existing natural key. Seed entries on
     repository reads and update them immediately when a new in-memory entity is queued.
   - Update the nine source processors (`*TeamImportProcessor`, `*PlayerImportProcessor`, and
     `*MatchImportProcessor`) to resolve through the run context. Preserve current source scoping,
     season scoping, RFETM licence/team-key behavior, BCNESA fixture splitting, malformed-record
     handling, and idempotency; do not introduce cross-run/static caches or name-only deduplication.
   - Ensure preview traversals use a fresh read-only/default run context and remain non-persisting.
     Concurrent API executions must never share cache state.

5. **Batch only writes supported by repository contracts and retain bounded memory/failure behavior.**
   - Add collection save methods, with compatibility-preserving default implementations, to
     `TeamRepository`, `PlayerSeasonRepository`, and `MatchRepository`. Override them with Spring Data
     `saveAll(...)` calls in `TeamRepositoryJpa`, `PlayerSeasonRepositoryJpa`, and
     `MatchRepositoryJpa`.
   - Reuse the existing collection-oriented `saveLineups`, `saveGames`, `saveSetScores`, and
     `saveDoublesPairs` ports. Do not add persistence APIs to the import module or make it depend on
     JPA.
   - Buffer a bounded number of complete match aggregates in `ImportRunContext` and flush in foreign-key
     order: teams, player seasons, matches, lineups, games, set scores, doubles pairs. Flush at the
     configured threshold and at traversal completion; clear cache/buffer state only after a
     successful flush.
   - Report a failed flush as a structured execution issue and stop the run. Do not claim run-wide
     rollback: existing adapters use method-level transactions, so a retry must remain safe through
     natural-key checks and source-scoped caches. A broader unit-of-work transaction is out of scope
     unless profiling demonstrates it is required and its lock/memory impact is separately approved.
   - Keep the existing Hibernate JDBC batch size and ordered insert/update settings in both runtime
     `application.yml` files aligned; make any batch-size override environment-driven.

6. **Run optional post-processing through the same service with safe adapter-specific policy.**
   - Inject and sequence the existing `RfetmClubConsolidationProcessor`,
     `TeamToClubConsolidationProcessor`,
     `FederatedClubToCanonicalClubConsolidationProcessor`, and
     `PlayerSeasonConsolidationProcessor` from `NavigatorImportExecutionService`.
   - Run club consolidation first and player consolidation second, only after successful traversal and
     persistence flush. Keep modes independent, preserve `WRITE`/`REPORT`, and keep both disabled by
     default.
   - Preserve source behavior: RFETM club consolidation requires the teams folder; BCNESA/FCTT use
     team-to-club consolidation; canonical-club consolidation follows source-specific club
     consolidation; player consolidation follows clubs. All consolidation scans remain source-scoped
     and cover the complete source inventory even for a season-filtered traversal.
   - Validate every requested option before traversal. Missing RFETM configuration or an error-bearing
     consolidation summary fails the execution; warnings remain visible but do not fail it. Return
     each phase's mode, duration, counters, warnings, and errors in `PostProcessingOutcome`.

7. **Convert the CLI and endpoint implementations into thin adapters.**
   - Refactor `tt-data-league-import-runtime/.../App.java` to parse
     `ImportRuntimeArguments`, build one request/options value, invoke `ImportExecutionService`, log
     the shared result, and exit with the existing explicit failure behavior. Remove its private
     source-dispatch and consolidation orchestration.
   - Keep every existing CLI flag and validation rule in `ImportRuntimeCliContract` and
     `ImportRuntimeArguments`; update `tt-data-league-import-runtime/README.md` only where the shared
     result, metrics, or configuration changes operational behavior.
   - Keep `/api/v1/administration/import/start` synchronous and authenticated. The endpoint continues
     accepting only `importResourceId`; it must derive source, season, and ACTAS path from the stored
     `ImportResource`, never from client-supplied paths.
   - Add typed API-runtime configuration under `tt.league.import.execution` for batch size and
     source-scoped consolidation policy. Environment-backed defaults disable consolidation; an RFETM
     teams folder is accepted only from server configuration. Map this policy in
     `NavigatorBackedImportResourceProcessService`.
   - Preserve `StartImportProcessCommandHandler`'s resource state transitions and retry behavior.
     Mark the resource `PROCESSED` only for a successful shared result; traversal, flush, or requested
     post-processing failure must finish it as `ERROR`.

8. **Add focused regression, wiring, and performance tests.**
   - In `tt-data-league-import`, test shared execution for all three sources, all-season versus
     season-filtered dispatch, exact processor order, normalized BCNESA metrics, structured processor
     issues, empty/failure status, post-processing order/modes, RFETM folder validation, and no
     post-processing after a failed traversal/flush.
   - Extend navigator tests to verify issue capture while peer processors still execute. Replace
     recording-decorator tests with executor/issue tests.
   - Extend `ImportProcessorsTest`, `BcnesaImportProcessorsTest`, and
     `FcttImportProcessorsTest` with counting repositories to prove one lookup per run cache key,
     no cache leakage between executions, exact source/season keys, bounded flushes, and idempotent
     retry after a failed flush.
   - In `tt-data-league-core-domain`, cover new collection-save defaults and extend
     `StartImportProcessCommandHandlerTest`/DTO mapping tests for execution metrics and
     post-processing failure state.
   - In `tt-data-league-core-repository-jpa`, add repository tests proving bulk saves preserve
     relationships, uniqueness, and source/season natural keys. Update
     `docs/rfetm-datamodel.md` only if persistence semantics or schema constraints change.
   - In `tt-data-league-import-runtime`, rewrite `AppTest` around a fake
     `ImportExecutionService` and retain argument/consolidation validation coverage.
   - In `tt-data-league-api-rest` and `tt-data-league-api-runtime`, retain controller routing coverage
     and add a Spring wiring/configuration test proving the API selects the shared executor, defaults
     consolidation off, and never accepts a client path.

9. **Validate and document the delivered behavior.**
   - Run:
     `mvn -pl tt-data-league-core-domain,tt-data-league-core-repository-jpa,tt-data-league-import,tt-data-league-import-runtime,tt-data-league-api-rest,tt-data-league-api-runtime -am test`.
   - Run the full reactor with `mvn test`.
   - Execute the post-change benchmark with the exact baseline fixture/database reset procedure and
     record the results in `# Notes`. If the 10% target is missed, keep the feature out of
     `in-review`, attach the measured bottleneck, and refine only the proven hot path.
   - Verify the API response remains consumable by the existing frontend normalizer and workspace;
     run the frontend test suite, lint, and build if the response contract changes.
   - Review the final diff for generated files, credentials, unrelated edits, module-boundary
     violations, synchronized SDD acceptance criteria, and required README/data-model updates.

# Acceptance Criteria

- [ ] CLI and API imports invoke the same `ImportExecutionService` and therefore use identical source dispatch, processor order, season filtering, traversal failure rules, and post-processing order.
- [ ] The shared execution result reports normalized traversal metrics, elapsed time, structured processor issues, persistence counters, and requested post-processing outcomes; the CLI logs it and the endpoint exposes a backward-compatible mapping.
- [ ] Every execution owns source/season-scoped team, player-season, and match caches, and supported repository writes are flushed in bounded batches without weakening identity, idempotency, or processor-failure isolation.
- [ ] Club and player consolidation remain independently opt-in, run only after successful traversal in club-then-player order, use the complete source-scoped inventory, and make the overall import fail when a requested phase fails.
- [ ] The endpoint accepts only the stored import-resource identity/path and server-side consolidation configuration; defaults perform no consolidation and no cache state is shared between requests.
- [ ] A documented like-for-like benchmark (same source, folder, season, restored database, JVM, and consolidation settings) shows the API shared-executor median within 10% of the CLI median across at least three measured runs, with no behavior or persisted-data regression.
- [ ] Focused domain, import, navigator, JPA adapter, CLI, REST, and API-runtime wiring tests pass, followed by the full Maven reactor; frontend checks pass if the response contract changes.

# Implementation Guidelines

- Keep dependency direction unchanged: domain repository/service ports in
  `tt-data-league-core-domain`, persistence implementations in
  `tt-data-league-core-repository-jpa`, reusable orchestration and source behavior in
  `tt-data-league-import`, and adapter configuration/argument parsing in the two runtime modules.
- Do not make `tt-data-league-import` depend on JPA or either runtime. Do not move source-specific
  parsing or reconciliation into `App`, the REST controller, or the command handler.
- Preserve API authentication, synchronous `/start` semantics, current resource transitions, and
  response fields. Making imports asynchronous or adding job persistence is a separate feature.
- Treat `itemsPersisted` as a compatibility field until real write counters replace its current
  dispatch-count semantics; do not silently change its meaning.
- Cache exact existing lookup keys only. Never add unscoped name matching, external IDs to
  `FederatedClub`/`FederatedPlayer`, cross-request state, or destructive/default consolidation.
- Preserve per-item processor isolation and diagnostic location while eliminating the endpoint-only
  recording decorators. Never swallow I/O, persistence, processor, or post-processing failures.
- Keep batches bounded and dependency ordered. Do not retain an entire multi-season import graph in
  memory, and do not assume `saveAll` alone enables JDBC batching without the runtime Hibernate
  settings.
- Benchmark before selecting optimizations. Do not add JMH or another framework unless the existing
  repeatable integration procedure proves insufficient.

# Notes

- Implementation started on 2026-09-05: registry moved from planned through ready to
  in-progress before code changes. The shared execution contract and navigator-backed
  executor now own source dispatch, season selection, normalized metrics, failure status,
  and optional club-then-player post-processing. CLI and import-resource adapters call that
  contract, and the legacy result/DTO retain their original fields while exposing execution
  metrics and outcomes.
- Added compatibility-preserving collection save methods for teams, player seasons, and
  matches with JPA `saveAll` implementations. Added an execution-local context type for
  subsequent exact-key processor cache integration.
- The feature remains in-progress: processors now receive an execution-owned `ImportRunContext`
  through RFETM, BCNESA, and FCTT navigator contexts, and navigator summaries retain structured
  processor issues with processor and report location. Lookup-cache consumption, bounded aggregate
  flushing, API-specific consolidation configuration, benchmark measurements, and the full
  focused/full test gates still require completion.
- Follow-up validation on 2026-09-05: `mvn -pl tt-data-league-import-runtime -am test`
  compiled the changed import module, then stopped at the pre-existing API REST test-source
  errors for missing `ImportJobController` and `InMemoryImportJobsService`; the requested
  import-runtime tests therefore did not execute. `mvn -pl tt-data-league-api-runtime -am
  test -DskipTests` likewise stopped at those same API REST test compilation errors. No API
  REST missing classes were changed. The new command-handler regression test covers the
  `FAILURE` result -> `ERROR` resource transition.
- Validation completed: `mvn -pl tt-data-league-import-runtime -am package -DskipTests`
  and `mvn -pl tt-data-league-core-repository-jpa -am test -DskipTests` compile successfully.
  The full `mvn test` remains blocked by existing JPA test classpath failures
  (`ImportResourceRepository.class` not found), while the API REST test-source compilation
  has existing missing `ImportJobController`/`InMemoryImportJobsService` classes and the
  import-runtime `AppTest` contains a pre-existing recursive helper. These blockers prevent
  claiming the full acceptance test gate or moving the registry to in-review.
- Focused `mvn -pl tt-data-league-import -am clean test` currently reports eight existing
  in-memory import/consolidation fixture assertion failures; no production exception was
  reported by those tests. The affected tests remain unresolved and prevent claiming the
  focused import test gate.
- Feature captured on 2026-09-05. The exact requested title is preserved in the feature registry and
  this details document's filename/link metadata.
- Planning completed on 2026-09-05 after inspecting the CLI, endpoint adapter, navigators, processor
  tests, domain repository ports, JPA adapters, runtime configuration, and consolidation pipeline.
- Confirmed baseline: both runtimes already use the same three navigator classes and the same
  Team(10) -> Player(20) -> Match(30) Spring processor ordering. The endpoint differs by rebuilding
  decorated processor lists, always selecting its resource season, mapping a detailed HTTP result,
  and omitting CLI post-processing.
- Confirmed persistence baseline: both runtimes already configure Hibernate JDBC batching at 50;
  game, lineup, set-score, and doubles-pair ports already accept collections, while team,
  player-season, and match writes are single-item and repeated exact lookups occur across processors.
- The recording decorators are duplicate error capture, but their performance impact is not yet
  measured. Database round trips are a likely larger hot spot, so the benchmark is an implementation
  gate rather than treating either cause as established fact.
- No planning blocker exists. Implementation benchmarking requires representative source exports and
  a restorable database snapshot; record their non-sensitive identifiers and results here when the
  feature is implemented.
