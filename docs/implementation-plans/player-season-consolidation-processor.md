# PlayerSeason consolidation processor implementation plan

## Goal and corrected consolidation boundary

Add an import-layer reconciliation processor that consolidates equivalent
player registrations onto one canonical, source-scoped `Player`.

`PlayerSeason` is the season-specific registration and `Player` is the
season-independent identity. Therefore, consolidation must preserve every
`PlayerSeason` row, UUID, source, licence, season, and season-specific name.
It must update only the `PlayerSeason` association through an immutable
`PlayerSeason.withPlayer(...)` operation. It must not delete registrations or
retarget `MATCH` or `LINEUP` references.

The prompt's requirement to create a new `Player` is interpreted as
create-or-reuse one canonical `Player` per safe group. Existing player
associations are retained when they agree; conflicting associations are not
silently merged.

## Scope and safety policy

1. The processor accepts an explicit `ImportSource` and inventories only
   `PlayerSeason` records from that source. No player or registration is
   resolved by an unscoped name when source identity matters.
2. Automatic consolidation is enabled for all current `ImportSource` values,
   as confirmed for this plan. Source-specific licence and identity rules
   remain available for later tightening.
3. Exact normalized-name groups are eligible automatically. Fuzzy candidates
   are eligible only when they pass token safeguards, a named score
   threshold, and mutual-best-match checks.
4. Blank names, empty normalized keys, ambiguous candidates, conflicting
   existing players, and persistence failures are reported explicitly.
   Unsafe groups are left unchanged.
5. The operation is idempotent. A second run performs no player creation or
   reassociation for already-canonical registrations.
6. The processor is an opt-in historical repair and must run once after a
   source traversal, not once per parsed report.

## Module and type design

Keep the processor in the import module without dependencies on JPA or Spring
Data:

- `org.cttelsamicsterrassa.data.load.shared.player.PlayerSeasonConsolidationProcessor`
  coordinates inventory, matching, canonicalization, persistence, and
  reporting.
- `PlayerNameNormalizer` provides Unicode-aware normalization, lowercasing with
  `Locale.ROOT`, punctuation handling, whitespace collapsing, token cleanup,
  and a small explicit abbreviation registry.
- `PlayerNameMatcher` returns a classified comparison result with normalized
  keys, significant tokens, score, and rejection reason rather than a bare
  boolean.
- Immutable report values such as `PlayerConsolidationSummary`,
  `ConsolidatedPlayer`, and `ConsolidationWarning` expose scanned rows,
  exact groups, accepted fuzzy groups, created players, reassociations,
  already-correct rows, and warnings/errors.

Use constructor injection and the existing repository-port style. Keep report
values independent from persistence entities and provide a concise
`toString()` for runtime logging.

## Domain and repository changes

### Domain

1. Add `PlayerSeason.withPlayer(Player)` that returns an otherwise identical
   registration while retaining its ID, source, name, licence, and season.
   Do not add a mutable public setter.
2. Preserve the existing `Player` identity model. Do not add `externalId` or
   source-system licence fields to `Player`.

### Repository ports

1. Extend `PlayerSeasonRepository` with a complete source-scoped inventory,
   for example `findAllPlayerSeasonsBySource(ImportSource source)`.
2. Add only the explicit player lookup or inventory needed for deterministic
   canonical selection, such as source-scoped name lookup. Do not fall back to
   `findPlayerByName` when source identity is required.
3. Update every in-memory implementation used by import tests to implement the
   new methods and preserve natural-key/idempotency behavior.

## Matching and canonicalization algorithm

For the requested source:

1. Load all `PlayerSeason` registrations and count them in the summary.
   Reject null or blank names with a warning.
2. Normalize names by:
   - Unicode normalization and case folding with `Locale.ROOT`;
   - trimming and collapsing repeated whitespace;
   - removing punctuation that does not carry identity;
   - normalizing verified common abbreviations only;
   - handling comma-separated and reordered name terms through a stable
     representation.
3. Group registrations by the exact normalized key. Empty keys are warnings,
   not matches.
4. For each exact group, select the canonical player deterministically:
   - retain the single existing player when every member agrees;
   - otherwise choose an existing player using stable registration ordering
     only when the group has no conflicting player IDs;
   - when no player exists, reuse a source-scoped canonical-name match or
     create one `Player` with the deterministic preferred display name.
5. Compare different exact groups for fuzzy candidates only when:
   - significant token sets are compatible;
   - names are not too short or one-token-only;
   - normalized edit-distance score meets a named threshold;
   - the candidate is the unique mutual best match.
6. Do not merge transitive fuzzy chains. Each registration must independently
   satisfy the safeguards for its canonical group.
7. Skip groups containing conflicting pre-existing player IDs unless a
   future source-specific identity policy explicitly authorizes the merge.
8. Reassociate only registrations whose current player differs from the
   selected canonical player. Save the `PlayerSeason` returned by
   `withPlayer(...)`, preserving all other fields.
9. Record canonical display names, registration IDs, matching mode, and
   rejection reasons in the immutable summary.

## Processing, errors, and reporting

- Log the source, scanned count, duplicate groups, canonical names, created
  players, reassociations, warnings, and errors.
- Do not log complete source payloads or unrelated sensitive data.
- Propagate persistence failures according to the surrounding import contract;
  do not turn failures into successful imports or silently continue.
- Prefer a transaction boundary per source run in the persistence/runtime
  integration so a write failure does not leave a partially reassociated
  group.
- If a report/dry-run mode is added, it must execute the same matching and
  canonical-selection path, return the same proposed counts, and perform no
  saves.

## JPA adapter and schema work

Update the JPA module only where required by the new source-scoped inventory
and reassociation:

1. Follow the existing `TeamRepositoryJpa` source mapping pattern. Add a
   private `mapFromImportSourceToSource(ImportSource source)` helper that
   returns `null` for a null input and otherwise calls
   `Source.valueOf(source.name())`. Use it for every source-scoped
   `PlayerSeason` helper query, rather than passing the domain enum directly
   to Spring Data.
2. Add the Spring Data inventory query
   `findAllBySource(Source source)` to `PlayerSeasonRepositoryHelper`, then
   implement `findAllPlayerSeasonsBySource(ImportSource source)` in
   `PlayerSeasonRepositoryJpa` by mapping the domain source to JPA `Source`,
   loading the rows, converting them with
   `PlayerSeasonJPAToPlayerSeasonMapper`, and returning the mapped list.
3. Preserve the same bidirectional mapping convention already used by the
   player mappers: `PlayerSeasonToPlayerSeasonJPAMapper` converts a non-null
   domain `ImportSource` with `Source.valueOf(playerSeason.getSource().name())`,
   while `PlayerSeasonJPAToPlayerSeasonMapper` converts a non-null JPA
   `Source` with `ImportSource.valueOf(playerSeasonJPA.getSource().name())`.
   Null handling must remain explicit in both directions.
4. Ensure the existing mappers persist the canonical `player_id` while
   retaining the registration ID and metadata.
5. Add or update repository integration coverage for loading registrations,
   changing `player_id`, and reloading the same registration.
6. Do not alter match or lineup foreign keys, cascades, or registration
   identity constraints. Update the schema contract documentation only if
   the actual JPA schema changes.

## Runtime integration

Add a separate opt-in runtime option, following the existing club
consolidation wiring:

1. Add a player-consolidation flag and optional report mode to
   `ImportRuntimeArguments`.
2. Add a runtime runner that invokes the processor once after the selected
   source traversal completes successfully.
3. Support `--season` for traversal selection while consolidating the whole
   selected source inventory by default.
4. Log the returned `PlayerConsolidationSummary` and document the option in
   `App` usage text.
5. Do not make consolidation the default import behavior.

## Tests

### Domain and import tests

Add focused JUnit 5 tests for:

1. `PlayerNameNormalizer`: case, whitespace, punctuation, accents, reordered
   terms, and only explicitly supported abbreviations.
2. `PlayerNameMatcher`: exact matches, accepted minor typos, short-name
   rejection, token mismatch, below-threshold scores, ambiguous candidates,
   and non-mutual best matches.
3. `PlayerSeasonConsolidationProcessor`:
   - source isolation;
   - exact duplicate grouping;
   - fuzzy matching and rejected candidates;
   - deterministic canonical player/name selection;
   - reuse of an agreed player;
   - creation of exactly one player when none exists;
   - conflicting existing players reported without writes;
   - preservation of every registration ID, licence, season, and name;
   - no match/lineup retargeting;
   - idempotent second run;
   - report mode with unchanged repositories;
   - malformed input and persistence error reporting.

Update `InMemoryRepositories` to provide complete source-scoped inventories
and to verify association replacement by ID.

### JPA and runtime tests

1. Extend `ImportSchemaTest` or add a focused repository test that persists
   one canonical player and multiple `PlayerSeason` rows, changes their
   association, and verifies the same registration IDs and metadata after
   reload.
2. Add runtime argument/runner tests proving consolidation is opt-in, runs
   after traversal, receives the selected source, and logs/returns the
   summary.

## Acceptance criteria

The implementation is complete when:

1. Safe duplicate `PlayerSeason` registrations are identified using
   source-scoped normalized and conservative fuzzy matching.
2. Each safe group has one deterministic canonical `Player`.
3. Every registration row and UUID is preserved, with only `player_id`
   reassociated through the domain API.
4. Match and lineup references remain unchanged.
5. Ambiguous, conflicting, malformed, and unsafe matches produce warnings and
   no unintended writes.
6. Actions and results are logged and returned in a summary report.
7. A second run is idempotent.
8. The processor is explicitly invokable from the import runtime.
9. Focused tests and the full Maven reactor build pass.

## Validation

Run the focused module tests as changes are introduced:

```text
mvn -pl tt-data-league-core-domain -am test
mvn -pl tt-data-league-core-repository-jpa -am test
mvn -pl tt-data-league-import -am test
mvn -pl tt-data-league-import-runtime -am test
```

Finish with:

```text
mvn test
```

## Decisions recorded for implementation

- Automatic processing applies to all current `ImportSource` values, with
  source-scoped matching and identity lookups.
- `PlayerSeason` registrations are preserved; consolidation changes only their
  `Player` association.
- External IDs are not added to `Player` or `PlayerSeason`.
- Fuzzy matching is conservative and non-transitive; uncertain cases are
  reported rather than merged.
- Runtime consolidation is opt-in and executes once after successful source
  traversal.
