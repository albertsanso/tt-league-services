# Build Plan

Manual club consolidation today is *lossy*. `ConsolidateClubsCommandHandler`
([source](../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/club/consolidate/ConsolidateClubsCommandHandler.java))
reassigns every `FederatedClub` of the non-primary clubs to the primary club,
then calls `clubRepository.deleteClubById(nonPrimaryClubId)`. After the request
returns there is no record anywhere of which club rows existed, what they were
called, who merged them, or when. This feature adds that record.

The domain already emits the right signal: `Club.consolidate(List<UUID>)`
publishes `ClubsConsolidatedEvent`
([source](../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/club/event/ClubsConsolidatedEvent.java))
into the entity's event list. **Nothing in this repository ever drains that
list** — `grep -rn "getEvents()\|EventBus" --include=*.java .` returns no hits
outside the `org.albertsanso.commons` jars. So the event-based trigger the
feature calls for is half-built: the event exists, the dispatch does not. Steps
3 and 4 below build the missing half.

Scope note: this plan persists and exposes consolidation history over REST. It
does **not** add a history screen to the frontend — see
`# Implementation Guidelines`.

## 1. Domain model: `ConsolidationAction`

New package `org.cttelsamicsterrassa.data.core.domain.consolidation` in
`tt-data-league-core-domain`, mirroring the layout of
`domain/load` (`model/`, `repository/`).

1. `model/ConsolidationActionType.java` — enum `MERGE`, `SPLIT`, `RENAME`. Only
   `MERGE` is produced by this feature; the other two are declared because the
   registry goal names them, and adding enum constants later to a
   `@Enumerated(EnumType.STRING)` column is free.
2. `model/ConsolidationActionClub.java` — immutable value object holding
   `UUID clubId` and `String clubName`. **Not** a reference to `Club`: source
   clubs are deleted by the same operation, so this must be a denormalized
   snapshot. Static factories `createNew`/`createExisting` are unnecessary; a
   record-style final class with a single constructor plus `of(UUID, String)`
   matches `ClubNameComparison` in the import module.
3. `model/ConsolidationAction.java` — extends `org.albertsanso.commons.model.Entity`
   like `Club` does. Fields:
   - `UUID id`
   - `ConsolidationActionType type` (non-null)
   - `ZonedDateTime occurredOn` (non-null)
   - `UUID performedByUserId` (nullable — see step 5 on unauthenticated/system callers)
   - `String performedByUsername` (nullable, snapshot; a user row may later be deleted)
   - `String canonicalName` (the resulting name)
   - `List<ConsolidationActionClub> sourceClubs` (the merged-away clubs)
   - `List<ConsolidationActionClub> targetClubs` (after a merge: exactly the
     surviving primary club; kept as a list so `SPLIT` fits the same shape)

   Provide `createNew(...)` (assigns `UUID.randomUUID()`) and
   `createExisting(UUID, ...)`, defensive `List.copyOf` in the constructor, and
   unmodifiable getters. Validate: type/occurredOn non-null, `canonicalName`
   non-blank, `sourceClubs` non-empty. Do **not** publish an event from this
   entity — it is the *result* of an event, and a second event would loop.
4. `repository/ConsolidationActionRepository.java` — domain port:

   ```java
   void save(ConsolidationAction action);
   Optional<ConsolidationAction> findById(UUID id);
   List<ConsolidationAction> findAllOrderByOccurredOnDesc();
   List<ConsolidationAction> findAllByClubId(UUID clubId);
   ```

   `findAllByClubId` must match a club appearing on **either** side, so an
   admin can ask "what happened to this id" for a club that no longer exists.

## 2. Persistence adapter

New package
`org.cttelsamicsterrassa.data.core.repository.jpa.consolidation` with
`model/`, `mapper/`, `impl/`, following `repository/jpa/load` exactly.

1. `model/ConsolidationActionJPA.java` — `@Entity`, `@Getter @Setter
   @NoArgsConstructor @AllArgsConstructor`, `@Table(name = "consolidation_action",
   indexes = @Index(name = "idx_consolidation_action_occurred_on", columnList = "occurred_on"))`.
   Columns: `id` (`@Id UUID`), `type`
   (`@Enumerated(EnumType.STRING)`, not null), `occurred_on` (`ZonedDateTime`,
   not null), `performed_by_user_id` (`UUID`, nullable), `performed_by_username`
   (`String`, nullable, length 255), `canonical_name` (not null, length 255).
   One `@OneToMany(mappedBy = "action", cascade = CascadeType.ALL,
   orphanRemoval = true, fetch = FetchType.LAZY)` to the child below,
   initialized with `new ArrayList<>()`.
2. `model/ConsolidationActionClubJPA.java` — `@Table(name =
   "consolidation_action_club", indexes = @Index(name =
   "idx_consolidation_action_club_club_id", columnList = "club_id"))`. Columns:
   `id` (`UUID`), `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name =
   "consolidation_action_id", nullable = false)`, `role`
   (`@Enumerated(EnumType.STRING)` over a new
   `ConsolidationActionClubRole { SOURCE, TARGET }`), `club_id` (`UUID`, not
   null), `club_name` (not null, length 255).

   **`club_id` must not be a `@JoinColumn` to `ClubJPA`.** The merged-away rows
   are deleted in the same transaction; an FK would either fail the insert or
   force a cascade that erases the audit record — the exact failure this
   feature exists to prevent. Keep it a plain `UUID` column and say so in a
   comment, because the surrounding entities all use real associations and a
   reviewer will otherwise "fix" it.
3. `mapper/ConsolidationActionToConsolidationActionJPAMapper.java` and
   `mapper/ConsolidationActionJPAToConsolidationActionMapper.java` — `@Component`
   classes implementing `Function<A, B>`, like `ClubToClubJPAMapper`. The
   to-JPA mapper sets the back-reference on each child before returning.
4. `impl/ConsolidationActionRepositoryHelper.java` — `JpaRepository<ConsolidationActionJPA, UUID>`
   with `List<ConsolidationActionJPA> findAllByOrderByOccurredOnDesc()` and a
   `@Query` for the club lookup:

   ```java
   @Query("select distinct a from ConsolidationActionJPA a join a.clubs c where c.clubId = :clubId order by a.occurredOn desc")
   List<ConsolidationActionJPA> findAllByClubId(@Param("clubId") UUID clubId);
   ```
5. `impl/ConsolidationActionRepositoryJpa.java` — `@Transactional @Component
   @AllArgsConstructor`, implements the port, delegating through the mappers.
   Copy `ImportResourceRepositoryJpa` for shape.
6. Schema: both runtimes use `spring.jpa.hibernate.ddl-auto: update`
   (`tt-data-league-api-runtime/src/main/resources/application.yml:44`,
   `tt-data-league-import-runtime/src/main/resources/application.yml:23`), so
   the two tables are created automatically. No migration scripts exist in this
   repository and none should be added here.
7. Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` with both
   tables, their columns, the `SOURCE`/`TARGET` role semantics, and an explicit
   note that `club_id` is intentionally FK-free.

## 3. Enrich and dispatch `ClubsConsolidatedEvent`

1. Extend `ClubsConsolidatedEvent` so a subscriber can build the record without
   re-reading rows that no longer exist. Add to the existing
   `primaryClubId` / `canonicalName` / `mergedClubIds`:
   - `String primaryClubName`
   - `Map<UUID, String> mergedClubNames` (or a `List<ConsolidationActionClub>`;
     prefer the latter to avoid a second shape for the same pair)
   - `UUID performedByUserId`, `String performedByUsername`

   Keep the private constructor plus a widened `of(...)` factory. This is the
   only caller-visible signature change in the domain event API, and
   `Club.consolidate` is its sole producer.
2. Change `Club.consolidate(List<UUID> mergedClubIds)` to
   `consolidate(List<ConsolidationActionClub> mergedClubs, UUID performedByUserId, String performedByUsername)`
   and have it publish the enriched event. `Club` already imports
   `ClubsConsolidatedEvent`, so no new dependency direction is introduced.
3. In `ConsolidateClubsCommandHandler.handle`, **capture each non-primary
   club's name before deleting it**. The current loop (lines 58-61) calls
   `clubRepository.deleteClubById(nonPrimaryClubId)` and the earlier
   `findMissingClub` check only proves existence; nothing holds the name. Build
   the `List<ConsolidationActionClub>` from `clubRepository.findClubById(id)`
   inside the loop, before the delete.

   Order matters for a second reason: `primaryClub.modifyName(...)` runs after
   the loop, so `canonicalName` in the event is the post-merge name while the
   source snapshots are pre-merge names. That is the intended semantics — state
   it in the handler's Javadoc.
4. Drain and publish. Inject `org.albertsanso.commons.event.EventBus` into the
   handler (`commons-core` is already a `tt-data-league-core-domain` dependency,
   so this respects the "domain must not depend on Spring or JPA" rule in the
   root `AGENTS.md`), and after `clubRepository.saveClub(primaryClub)`:

   ```java
   primaryClub.getEvents().stream()
           .filter(DomainEvent.class::isInstance)
           .map(DomainEvent.class::cast)
           .forEach(eventBus::publish);
   ```

   Publish **after** the save, so a subscriber never records an action for a
   merge that failed to persist.

   Note `Entity.getEvents()` has no clear/drain method in `commons-core`
   (`javap org.albertsanso.commons.model.Entity` shows only `publishEvent`,
   `getEvents`, `hasEvents`). The handler builds `primaryClub` fresh from the
   repository on every call, so there is no cross-request accumulation — but
   `modifyName` also queues a `ClubNameModifiedEvent`, which the filter above
   will publish too. That is harmless (no subscriber handles it) and is the
   correct aggregate-wide semantics; do not narrow the drain to a single event
   type.

## 4. Subscriber that writes the record

1. `application/club/consolidate/ClubsConsolidatedEventSubscriber.java` in
   `tt-data-league-core-domain`, `@Named`, extending
   `DomainEventSubscriber<ClubsConsolidatedEvent>` with an `@Inject`
   constructor taking `ConsolidationActionRepository`. `handles()` is already
   implemented generically in the base class; override `handle(ClubsConsolidatedEvent)`
   to build a `ConsolidationAction.createNew(MERGE, event.getOccurredOn(), ...)`
   and `save` it. `Event.getOccurredOn()` is populated by the event's own
   constructor, so the action timestamp comes from the event, not from a second
   `ZonedDateTime.now()`.
2. Wire the bus. `SynchronousEventBus` is `@Named` with an `@Inject`
   constructor taking `List<DomainEventSubscriber<? extends DomainEvent>>`, so
   Spring assembles it with no configuration class — **provided the module both
   depends on `eventbus-synchronous-inmemory` and component-scans
   `org.albertsanso.commons`**.
   - `tt-data-league-api-runtime` already satisfies both (`pom.xml:63`,
     `APIApplication.java` `scanBasePackages`). Nothing to do.
   - `tt-data-league-import-runtime` satisfies **neither** (its `pom.xml` has no
     eventbus dependency and `App.java:24` scans only
     `org.cttelsamicsterrassa`). Because `ConsolidateClubsCommandHandler` is
     `@Named` under `org.cttelsamicsterrassa`, it *is* a bean in that context,
     and adding a required `EventBus` constructor parameter will fail the
     import runtime's startup with `NoSuchBeanDefinitionException`. Add the
     `eventbus-synchronous-inmemory` dependency to
     `tt-data-league-import-runtime/pom.xml` and add `"org.albertsanso.commons"`
     to `App.java`'s `scanBasePackages`, matching `APIApplication`. An empty
     subscriber list injects fine.

   Do this in the same commit as step 3.4; splitting them leaves the import
   runtime unbootable on an intermediate commit.

## 5. Carry the acting user from REST down to the command

1. Add `UUID performedByUserId` and `String performedByUsername` to
   `ConsolidateClubsCommand`. Do **not** add them to `ConsolidateClubsRequest` —
   identity comes from the authenticated principal, never from the request
   body, or the audit trail is forgeable by its own subject.
2. In `ClubController.consolidateClubs`, take `Authentication authentication`
   as a second parameter and resolve the user the way
   `UserController.resolveCurrentUserId` does
   ([UserController.java:185-189](../../tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/user/UserController.java)):
   `@Autowired UserService userService`, then
   `userService.getUserByUsername(authentication.getName())`. Pass both the id
   and `authentication.getName()` into the command.
3. The endpoint is already `@PreAuthorize("hasAuthority('clubs:write')")`, so
   `authentication` is non-null in production. Still tolerate null (record
   `null` id / `"system"` username) rather than rejecting — an audit write must
   not be the thing that fails a consolidation.
4. Thread the two fields through the handler into `Club.consolidate(...)`
   (step 3.2).

## 6. Read endpoint

`GET /api/v1/clubs/consolidations` on `ClubController`, and
`GET /api/v1/clubs/{id}/consolidations` for the per-club view.

1. Application query `FindConsolidationActionsQuery` +
   `FindConsolidationActionsQueryHandler` under
   `application/consolidation/find/`, following the
   `FindClubsByStringInNameQuery` pair for shape, returning a
   `ConsolidationActionReadModel` (`dto/`).
2. `ConsolidationActionDto` in
   `tt-data-league-api-rest/.../club/` with a `fromObject` static factory, as
   every other DTO in that package has.
3. Guard both endpoints with `@PreAuthorize("hasAuthority('clubs:write')")` —
   consolidation history is an administrative view, and `clubs:write` is
   already the authority gating the write side.
4. Add the `@Operation` / `@ApiResponses` annotations in the style of the
   neighbouring endpoints.

## 7. Tests

1. `ConsolidateClubsCommandHandlerTest` (existing) — add a fake `EventBus` to
   the existing in-memory fixtures and assert that a single
   `ClubsConsolidatedEvent` is published, that it carries the **pre-merge**
   names of every source club, the post-merge canonical name, and the acting
   user. Extend the existing `InMemoryClubs` fixture rather than adding a new
   one. The constructor signature change touches every test in the class.
2. New `ClubsConsolidatedEventSubscriberTest` in the domain module with an
   in-memory `ConsolidationActionRepository`: asserts the saved action's type,
   timestamp, user, canonical name, and both club lists.
3. New `ConsolidationActionRepositoryJpaTest` in
   `tt-data-league-core-repository-jpa/src/test/java` using the existing Spring
   Boot + H2 setup. Must include the regression case that motivates step 2.2:
   persist an action whose `club_id` values reference **deleted** club rows and
   assert the read-back succeeds. Also cover `findAllByClubId` matching on the
   source side and on the target side.
4. `ClubControllerTest` (existing) — add cases for the enriched command
   (authenticated principal reaches the command) and for both read endpoints,
   including the 403 path for a caller without `clubs:write`.
5. Run:

   ```bash
   mvn -pl tt-data-league-core-domain,tt-data-league-core-repository-jpa,tt-data-league-api-rest -am test
   ```

   Then a full `mvn test` from the root, because step 4.2 changes the import
   runtime's Spring wiring and
   `tt-data-league-api-runtime/src/test/.../ImportExecutionConfigurationTest`
   loads a context.

## 8. Documentation

1. `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` — the two new
   tables (step 2.7).
2. Root `AGENTS.md` "Identity and data integrity" already states that club
   consolidation is opt-in and non-destructive-by-default; append a sentence
   that every *manual* consolidation now leaves a `consolidation_action` audit
   record, and that its club columns are deliberately FK-free snapshots.
3. Note in `# Notes` below that this feature introduces the first live use of
   the domain event bus in this repository.

# Implementation Guidelines

- **Dependency direction.** `ConsolidationAction`, its port, and the subscriber
  live in `tt-data-league-core-domain` and may import only `org.albertsanso.commons`
  and JDK types — no Spring, no JPA, per the root `AGENTS.md`. `javax.inject`
  (`@Named`/`@Inject`) is the annotation set the domain module already uses;
  keep to it, not `@Component`/`@Autowired`.
- **The audit record must never break the merge.** If the subscriber throws,
  the consolidation is already persisted; `SynchronousEventBus` dispatches in
  the calling thread, so an unchecked exception would propagate out of the
  handler and surface as a 500 on an operation that actually succeeded. Catch
  and log inside `ClubsConsolidatedEventSubscriber.handle`. Do not make the
  subscriber transactional together with the merge.
- **Snapshots, not references.** Every club name and id stored on a
  `ConsolidationAction` is a value copied at merge time. Never resolve those ids
  back through `ClubRepository` when reading history — most source ids will not
  resolve, by design.
- **No external-id fields.** The root `AGENTS.md` prohibition on `externalId`
  on `Club`/`FederatedClub` applies here too: `ConsolidationAction` records
  canonical `Club` UUIDs only, never RFETM team keys or licences.
- **Out of scope:** the automated import-time consolidation path
  (`RfetmClubConsolidationProcessor`, `FederatedClubToCanonicalClubConsolidationProcessor`,
  `PlayerSeasonConsolidationProcessor` in `tt-data-league-import`). Those run
  per-import over thousands of clubs and already produce
  `ClubConsolidationSummary` reports; routing them through this table is a
  separate feature. The registry goal says *manual* consolidation, and the
  trigger point in this plan — `ConsolidateClubsCommandHandler` — is reachable
  only from `POST /api/v1/clubs/consolidate`.
- **Out of scope:** a frontend history view.
  `tt-data-league-frontend/src/pages/ClubsConsolidationPanel.jsx` performs the
  merge but the acceptance criteria stop at the persisted record; step 6 exists
  so a UI feature can be built on a stable contract without reopening the
  backend.
- **Out of scope:** undo/rollback of a consolidation. The record is an audit
  trail, not an event-sourcing log — `FederatedClub` reassignment is not
  replayable from it.
- **Not player consolidation.** `PlayerSeasonConsolidationProcessor` has no
  manual/REST entry point today; this feature covers clubs only.

# Notes

- **2026-09-20 — plan created.** The feature was registered as `planned`
  without a details file; this document fills that gap. No prior plan content
  existed to preserve.
- **Decision: event-based, as the registry goal specifies.** The alternative —
  having `ConsolidateClubsCommandHandler` call `ConsolidationActionRepository`
  directly — is fewer moving parts and matches every other handler in the
  codebase. It was rejected because the goal explicitly asks for the
  event-published trigger and because `ClubsConsolidatedEvent` already exists
  for precisely this purpose. The cost is real and is carried by step 4.2: this
  is the **first** live use of the event bus here, so the wiring has to be
  established, including in `tt-data-league-import-runtime`, which does not
  currently have it.
- **Open question — retention.** Nothing prunes `consolidation_action`. At the
  manual-merge rate this is negligible, but if the import-time consolidation
  paths are ever routed here (explicitly out of scope above), a retention
  policy becomes mandatory. Not addressed in this plan.
- **Open question — `SPLIT` / `RENAME`.** The enum declares them; no operation
  produces them. `ModifyFederatedClubNameCommand` is the natural `RENAME`
  producer and there is no split operation at all. Left unimplemented
  deliberately rather than inventing semantics.
- **Constraint discovered — `Entity.getEvents()` does not drain.**
  `org.albertsanso.commons.model.Entity` exposes only `publishEvent`,
  `getEvents`, and `hasEvents`; there is no `clearEvents`. Safe here because
  each `Club` instance is short-lived, but any future handler that publishes
  from a long-lived or cached entity will re-publish. Worth raising against
  `commons-core` separately.

- **2026-09-20 — implemented; status `planned` → `in-progress` → `in-review`.**
  All eight plan steps landed. Both acceptance criteria verified against
  delivered behaviour:
  - *Record created on a manual consolidation*: `ConsolidateClubsCommandHandler`
    publishes to the `EventBus` after the merge is persisted;
    `ClubsConsolidatedEventSubscriber` writes the `ConsolidationAction`. Covered
    by `ConsolidateClubsCommandHandlerTest` (8 tests, incl. "publishes nothing
    when the consolidation is rejected") and
    `ClubsConsolidatedEventSubscriberTest` (4 tests).
  - *Record includes user id, source/target club names and ids, timestamp and
    type*: asserted field-by-field in the subscriber test, round-tripped through
    the database in `ConsolidationActionRepositoryJpaTest` (7 tests), and the
    principal-to-command path asserted in `ClubControllerTest`.
- **Validation.** `mvn test` from the root. New tests: 8 domain handler,
  4 subscriber, 7 persistence, 5 controller — all green, plus api-rest 65/65,
  api-runtime 15/15, api-mcp 16/16.
- **Pre-existing failures, unchanged by this work** (each confirmed identical on
  a clean `HEAD` checkout, so the plan's "full `mvn test`" gate cannot be met
  and was not met before this feature either):
  - `tt-data-league-core-domain`: `InitialUserProvisioningServiceTest`, 4
    failures — provisioning saves 3 default users where the test expects 2.
  - `tt-data-league-core-repository-jpa`: 36 errors. The module-wide
    `JpaTestApplication` component-scans the whole `org.cttelsamicsterrassa`
    tree, so it also picks up domain `@Named` services needing
    `ImportRunRegistry` and `ObjectMapper` beans that only the API runtime
    supplies; its context does not start.
  - `tt-data-league-import`: 8 failures across the import-processor tests.
  - `tt-data-league-import-runtime`: `AppTest`, 3 errors — `StackOverflowError`
    from unbounded recursion in the test's own `app()` helper.
- **Deviation from step 7.3 — test context.** The plan assumed the JPA module's
  `@SpringBootTest` setup worked. It does not (above). `ConsolidationActionRepositoryJpaTest`
  therefore reuses the workaround `FederatedPlayerRepositoryJpaTest` already
  applies: a `@TestConfiguration` supplying mock `ImportRunRegistry` and
  `ObjectMapper` beans. An attempt to give the test its own narrower
  `@SpringBootConfiguration` instead was reverted — `JpaTestApplication`'s scan
  picked it up and double-registered every Spring Data repository, which broke
  `FederatedPlayerRepositoryJpaTest`.
- **Addition to step 4.2 — a third context needed the event bus.** The plan
  identified `tt-data-league-import-runtime` as the runtime that would fail on
  the new `EventBus` dependency. The JPA module's `JpaTestApplication` had the
  same gap and failed the same way; it now scans `org.albertsanso.commons` and
  the module carries a test-scoped `eventbus-synchronous-inmemory` dependency.
- **Deferred from step 3.1.** `ClubsConsolidatedEvent` carries
  `List<ConsolidationActionClub>` rather than `Map<UUID, String>`, as the plan
  preferred. This makes the domain `club.event` package depend on
  `domain.consolidation.model`; both are inside the domain module, so no
  dependency rule is crossed, but the value object is now shared vocabulary
  rather than consolidation-private.
- **Not done — frontend.** Unchanged, as scoped. `ClubsConsolidationPanel.jsx`
  still performs merges and shows no history; the two read endpoints exist for a
  later UI feature to build on.
