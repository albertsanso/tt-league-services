# Team-to-`FederatedClub` consolidation implementation plan

## Goal and corrected domain boundary

Build an import-layer reconciliation process that identifies equivalent
season-specific `Team` registrations and associates them with one
source-scoped `FederatedClub`. The selected federated identity may point to one
season-independent canonical `Club`.

The prompt's phrase "create a new consolidated Club" must be implemented
through the existing model rather than by replacing team relationships:

- `Team` is the season-specific registration. It retains its UUID, source,
  season, and imported name and has an optional `FederatedClub` association.
- `FederatedClub` is the source-scoped identity and has an optional canonical
  `Club` association.
- `Club` is the canonical, season-independent identity and is matched globally
  only by its exact canonical display name.
- `MATCH` and `LINEUP` references target `Team` rows and must not be retargeted
  to a `Club` or rewritten during consolidation.

The process therefore preserves every `Team` row and UUID, updates only the
association through `Team.withFederatedClub(...)`, and saves the replacement
team object. It must not delete teams, merge registrations, alter `Team.name`,
or add `externalId` fields to `Team`, `FederatedClub`, or `Club`.

## Scope and safety policy

1. Accept an explicit `ImportSource` and inventory only teams from that
   source. Every federated lookup is source-scoped; an unscoped name lookup is
   never used to identify a federated entity.
2. Keep consolidation opt-in. The runtime must not run it as a side effect of
   an ordinary import. Write mode is selected explicitly with
   `--consolidate-clubs` or `--consolidate-clubs=write`; report mode is
   `--consolidate-clubs=report`.
3. Enable automatic rules for FCTT and BCNESA initially. RFETM remains
   disabled in the shared name-based processor until a key-aware policy can
   distinguish federation teams from competition registrations. An RFETM
   request must produce an explicit error or unsupported-policy report and no
   writes.
4. Exact normalized groups are eligible when their rules provide a complete,
   explainable identity key. Fuzzy candidates require compatible significant
   tokens, a named score threshold, a unique mutual-best match, and no
   conflicting existing associations.
5. Unknown suffixes, malformed or mojibake text, ambiguous candidates,
   conflicting federated-club IDs, empty normalized keys, and unsafe
   short-name matches produce warnings and no automatic merge.
6. Do not form transitive fuzzy groups. If `A` matches `B` and `B` matches
   `C`, `A` and `C` are not merged unless each independently satisfies the
   same canonical group's rules.
7. Write mode is idempotent. A second run creates no duplicate federated or
   canonical clubs and performs no reassociation for already-correct teams.
   Orphaned clubs are not deleted.
8. Execute one source run transactionally after successful traversal. A
   persistence failure must surface through the existing import contract and
   must not become a successful partial repair.

## Current surfaces to preserve and extend

The implementation should use the existing public types and source-specific
processors rather than introduce parallel identity models:

- Domain: `Team`, `FederatedClub`, `Club`, `TeamRepository`,
  `FederatedClubRepository`, and `ClubRepository`.
- Persistence: `TeamJPA`, `FederatedClubJPA`, their mappers and repository
  helpers, with `TEAM.federated_club_id` and
  `FEDERATED_CLUB.club_id` remaining nullable where the schema permits legacy
  data.
- Import: source-neutral code under
  `org.cttelsamicsterrassa.data.load.shared.club`; it must not depend on JPA
  or Spring runtime classes.
- Runtime: `App`, `ImportRuntimeArguments`,
  `ImportRuntimeCliContract`, and the runtime README.
- Tests: focused import tests and in-memory repositories, JPA schema/repository
  tests, and runtime argument/sequencing tests.

Do not restore the former `ClubSeason` terminology or introduce compatibility
wrappers for it. Do not add persistence annotations to domain classes or
import dependencies on the JPA adapter.

## Name parsing and matching policy

### Immutable name parts

Add immutable import-layer values for the parsed name and its evidence. The
parts should retain:

- original valid UTF-8 display text;
- Unicode-normalized and case-folded comparison tokens;
- diacritic-folded comparison keys where appropriate;
- significant identity tokens;
- canonical display candidates;
- removed qualifier types and source policy;
- applied reviewed aliases;
- confidence and rejection/review reasons.

Keep matching classification explicit, such as `EXACT`, `RULED_VARIANT`,
`FUZZY`, `REJECTED`, and `REVIEW_REQUIRED`. A matcher result must expose the
keys, score, and reasoning rather than return a bare boolean.

### Normalization rules

Apply rules in a controlled, source-aware order:

1. Validate non-null, non-blank text and detect malformed or obvious
   mojibake input.
2. Apply Unicode normalization, case folding with `Locale.ROOT`, trimming,
   whitespace collapsing, and controlled punctuation normalization.
3. Fold accents for comparison keys without losing accents in the preferred
   canonical display name.
4. Remove a standalone terminal `A`, `B`, or `C` only when it is a team
   designator. Treat punctuation-wrapped forms such as `-A-`, `-B-`, and quoted
   letters as equivalent only at the terminal position.
5. Remove reviewed category/team suffixes as a complete terminal sequence,
   including `Sen A/B` and `Vet A/B/C` and the reviewed multi-team variants
   illustrated by the prompt. Do not remove a meaningful letter or number
   embedded in a club name; `2000` remains significant.
6. Apply only reviewed aliases demonstrated by representative fixtures, such
   as `ST` to `SANT`, optional `DE`/`ELS` particle handling, and curated venue
   or sponsor-prefix rules. The sponsor rule for the `ÀNECBLAU` examples must
   be explicit and must not become a generic prefix stripper.
7. Preserve meaningful tokens after removing qualifiers. Names that share only
   a generic prefix, including `CLUB TENNIS TAULA` or `CLUB TENIS DE MESA`,
   remain distinct unless their complete identity tokens match.
8. Treat unknown abbreviations, suffixes, and aliases as review cases rather
   than guessing their meaning.

### Matching stages

Compare names in this order:

1. Folded exact key.
2. Team/category-neutral rule-derived key.
3. Approved spelling and particle-neutral key.
4. Reviewed sponsor/venue-neutral key.
5. Conservative fuzzy comparison only inside a shared rule-derived core.

Fuzzy matching must reject one-token or very-short names, incompatible
significant-token sets, below-threshold scores, ambiguous candidates, and
non-mutual best matches. Define and test a named threshold constant or policy
value; do not hide an unexplained numeric literal in the algorithm.

## Canonical identity and display-name selection

For each accepted group:

1. Retain one existing `FederatedClub` when all associated teams agree on its
   ID. If multiple pre-existing IDs conflict, report the conflict and leave
   the group unchanged unless a future source-specific policy authorizes it.
2. Otherwise reuse a source-scoped federated club only when the exact
   source/name identity is unambiguous; if none exists, create exactly one
   `FederatedClub` for the group.
3. Select the canonical display name independently from the matching key.
   Prefer an existing agreed canonical identity, then the candidate with the
   fewest removed qualifiers, complete reviewed spellings and meaningful
   particles, highest source frequency, earliest season, normalized lexical
   order, and finally Team UUID as deterministic tie-breakers.
4. Reuse or create the canonical `Club` through exact display-name lookup.
   Never use source-specific fuzzy matching as a cross-source canonical-club
   lookup. If an existing canonical association or display-name choice is
   ambiguous, report it instead of renaming or merging silently.
5. Link the selected federated club to the canonical club through
   `FederatedClub.withClub(...)` and persist the canonical club before its
   federated reference when a new canonical club is required.
6. Associate every accepted group member through `Team.withFederatedClub(...)`
   and `saveTeam(...)`, preserving each team's original name, season, source,
   UUID, and all match/lineup references.

The canonicalization must produce the expected results from the prompt,
including:

- `CC SANT ANDREU` with its `A`, `B`, and category variants;
- `CETT SANT ANDREU DE LA BARCA` and the reviewed `ST` spelling;
- `CTT COLLBATO` with `LA CASSOLA` and team variants;
- `CTT DELS HORTS 2000`, retaining `2000`;
- `CTT ELS AMICS DE TERRASSA` and reviewed particle variants;
- the curated `ÀNECBLAU - TT ...` sponsor-prefix example;
- terminal `Sen`/`Vet` category sequences;
- accented `LA BISBAL DEL PENEDÈS` and `COLÒNIA GÜELL`;
- punctuation-wrapped terminal letters;
- quoted team letters, duplicate names, and `CASSA`/`CASSÀ`;
- `OBERENA` and `CLUB TENNIS TAULA TRAMUNTANA FIGUERES` variants.

The two negative prompt datasets are mandatory regression cases: the
different clubs sharing `CLUB TENNIS TAULA` and the different clubs sharing
`CLUB TENIS DE MESA` must remain separate, with only the actual
`TRAMUNTANA FIGUERES` team variants grouped.

## Domain and repository changes

1. Keep `Team.withFederatedClub(...)` as the immutable association-replacement
   API. Verify its no-op behavior for an identical association and ensure it
   does not expose a mutable club setter.
2. Ensure `TeamRepository` exposes a complete
   `findAllTeamsBySource(ImportSource)` inventory and that every adapter and
   in-memory implementation returns all source rows in deterministic order.
   Do not implement reconciliation by repeatedly calling similar-name
   queries.
3. Keep `FederatedClubRepository.findFederatedClubBySourceAndName(...)`
   explicitly source-scoped. Make duplicate results fail clearly rather than
   selecting an arbitrary row. Add only the exact lookup or inventory methods
   needed for deterministic canonical selection.
4. Keep `ClubRepository.findClubByExactName(...)` as the canonical lookup.
   Do not fall back to a source-unscoped fuzzy lookup.
5. Add immutable summary/report values, including scanned registrations,
   exact groups, accepted fuzzy groups, federated clubs created, canonical
   clubs created, reassociations, already-correct registrations,
   consolidations, and warning/error details. Include source, canonical
   display name, registration IDs/names, matching mode, and rejection reason
   without exposing source payloads.
6. Update `InMemoryRepositories` and all affected tests to preserve natural-key
   behavior, source isolation, association replacement by ID, and report-mode
   write isolation.

## Source import integration

Restore the normal source-specific identity flow in:

- `RfetmTeamImportProcessor`;
- `BcnesaTeamImportProcessor`;
- `FcttTeamImportProcessor`;
- dependent match processors where team resolution must use the same imported
  identity.

Each processor should resolve or create a source-scoped `FederatedClub` and
its exact canonical `Club` before creating the season-specific `Team`.
Preserve source-specific identity rules:

- RFETM must retain its key-aware behavior and must not be replaced by
  name-only consolidation; match lookup should use the imported
  `ActaTeam.name()` when usable and use `RfetmClubKey.name()` only as the
  explicit fallback.
- BCNESA may use its narrow quoted-team-letter normalization.
- FCTT must not interpret RFETM-shaped payload IDs as FCTT identity.

The post-import reconciler repairs historical spelling variation; it is not a
replacement for correct import-time identity resolution and must not run once
per match report.

## JPA adapter and schema work

1. Implement the source inventory using `TeamRepositoryHelper.findAllBySource`
   and the existing `ImportSource` to JPA `Source` conversion. Keep null
   handling explicit and map results through the existing team mapper.
2. Verify that `TeamJPA.federatedClub` maps
   `TEAM.federated_club_id` as a lazy nullable relationship and that
   `FederatedClubJPA.club` maps `FEDERATED_CLUB.club_id` as a lazy nullable
   relationship with no cascade that would delete canonical clubs.
3. Persist association replacements through the existing mappers, retaining
   team IDs, names, source, season, and all registration metadata.
4. Add repository integration coverage for source inventory, canonical-club
   lookup, association persistence, and reload of the same Team IDs.
5. Do not alter match or lineup foreign keys, natural keys, constraints,
   cascades, or table names. Update
   `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if the
   implementation changes the documented persistence behavior. Do not add
   generated `target/` output or invent a migration when the current schema
   already contains the required federated links.

## Processor and runtime integration

Add a source-neutral `TeamToClubConsolidationProcessor` under
`org.cttelsamicsterrassa.data.load.shared.club`, with constructor-injected
domain repository ports. Its public operation should accept the source and
mode, for example:

```text
consolidate(ImportSource source, ConsolidationMode mode)
```

The processor must:

1. Load the complete source inventory, even when the runtime traversal was
   restricted by `--season`.
2. Parse and classify every valid name before grouping.
3. Produce one deterministic proposal set used by both write and report modes.
4. In report mode, perform no club, federated-club, or team saves.
5. In write mode, persist only accepted proposals and return the same summary
   counts that report mode calculated for the same starting state.
6. Log source, scanned count, group counts, created identities,
   reassociations, already-correct teams, warnings, and errors using
   identifiers/names needed for operations, never complete source payloads.

Wire `App` so the processor runs once only after successful traversal and
after the match/lineup import stages have completed. Preserve explicit
argument validation, source selection, required `--actas-folder` handling,
and failure propagation. Document all modes and the whole-source behavior
when `--season` is also supplied in `ImportRuntimeCliContract` and
`tt-data-league-import-runtime/README.md`.

If player consolidation is requested in the same run, preserve the existing
ordering: club consolidation first, player consolidation second. Keep the two
flags and report modes independent.

## Tests

### Domain and import tests

Add focused JUnit 5 tests for:

1. `ClubNameNormalizer` and name parts: case, repeated whitespace,
   punctuation, Unicode accents, quoted and wrapped letters, category
   suffixes, reviewed aliases, particles, sponsor/venue rules, invalid text,
   and preservation of canonical display accents.
2. `ClubNameMatcher`: exact and ruled matches, accepted fuzzy typos,
   significant-token mismatch, short-name rejection, below-threshold scores,
   ambiguous candidates, non-mutual best matches, and non-transitive chains.
3. All positive prompt examples and both negative shared-prefix datasets,
   asserting one canonical name per valid group and no false consolidation.
4. Source isolation: identical names in different sources receive different
   `FederatedClub` IDs while permitted canonical `Club` reuse remains exact
   and explicit.
5. Existing agreed association reuse, creation of exactly one federated and
   canonical club when missing, canonical display-name ranking, and
   conflicting association warnings with no writes.
6. Preservation of every Team ID, source, season, and name, plus unchanged
   match and lineup references.
7. Idempotent second write run and report mode with unchanged repositories and
   matching proposed counts.
8. RFETM policy rejection, malformed input, unknown aliases, persistence
   failures, and structured summary warnings.
9. RFETM, BCNESA, and FCTT first-import behavior, including source-scoped
   federated-club creation, canonical linkage, dependent match resolution,
   and repeat-import idempotency.

### JPA and runtime tests

1. Extend `ImportSchemaTest` or add a focused repository test that persists a
   canonical `Club`, a source-scoped `FederatedClub`, multiple seasonal Teams,
   a match, and lineups. Reassociate the Teams and assert that the federated
   link changes while Team IDs and match/lineup foreign keys remain identical.
2. Test complete source inventory and source/name lookup behavior, including
   duplicate-result failure and nullable legacy associations.
3. Test argument parsing for write/report modes, invalid modes, source
   selection, and `--season`.
4. Test runtime sequencing: traversal must succeed before consolidation,
   consolidation runs once against the selected source, report mode performs
   no saves, and player consolidation follows club consolidation when both are
   requested.

## Acceptance criteria

The implementation is complete when:

1. Safe duplicate Team registrations are found with source-scoped,
   explainable normalization and conservative fuzzy matching.
2. Every accepted group maps to one deterministic source-scoped
   `FederatedClub` and, where applicable, one exact-name canonical `Club`.
3. All Team rows, UUIDs, names, seasons, match references, and lineup
   references are preserved; only `Team.federatedClub` is reassociated through
   the domain API.
4. All prompt positive examples pass and the two shared-prefix negative
   datasets remain separate.
5. Ambiguous, conflicting, malformed, unsupported, and unsafe matches produce
   warnings/errors and no unintended writes.
6. Write and report modes use the same matching/proposal path, with report mode
   performing no persistence writes.
7. A second write run creates no duplicates and performs no unnecessary
   reassociations.
8. Consolidation is opt-in, runs once after successful traversal, scans the
   complete selected-source inventory despite `--season`, and preserves the
   required club-before-player ordering.
9. Import, domain, JPA, runtime, and full-reactor tests pass, and the schema
   and runtime documentation accurately describe the behavior.

## Implementation order and validation

1. Inspect current fixtures, source-specific identity rules, domain ports,
   mappers, and runtime sequencing. Correct representative UTF-8 fixtures
   before adding aliases.
2. Add immutable name parts, rule/policy types, normalizer, matcher, and
   focused unit tests without persistence dependencies.
3. Complete domain repository contracts and in-memory repositories, then
   implement grouping, canonical selection, warnings, summaries, deterministic
   writes, and report-mode isolation.
4. Restore source-scoped federated/canonical identity creation in RFETM,
   BCNESA, and FCTT import processors and align dependent match lookup.
5. Verify JPA inventory and nullable association mappings; update the schema
   contract only for actual persistence changes and add reload coverage.
6. Wire runtime arguments and post-traversal invocation, document CLI behavior,
   and add sequencing/transaction tests.
7. Run focused tests as boundaries change:

   ```text
   mvn -pl tt-data-league-core-domain -am test
   mvn -pl tt-data-league-core-repository-jpa -am test
   mvn -pl tt-data-league-import -am test
   mvn -pl tt-data-league-import-runtime -am test
   ```

8. Finish with the full reactor:

   ```text
   mvn test
   ```

Review the final diff for accidental generated files, secrets, old
`ClubSeason` terminology, unscoped lookups, match/lineup rewrites, and changes
outside the requested consolidation scope.
