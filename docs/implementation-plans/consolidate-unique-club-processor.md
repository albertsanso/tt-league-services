# Unique club consolidation processor implementation plan

## Goal and corrected consolidation boundary

Add an import-layer reconciliation processor that attaches equivalent
`Team` registrations to one source-scoped `FederatedClub`, while linking that
federated identity to a season-independent canonical `Club`. It must repair
registrations created by the current import processors without merging or
deleting `Team` rows.

This distinction is required by the existing model:

- `Team` is the season-specific team/registration record and owns an
  optional `FederatedClub` association.
- `FederatedClub` is the source-scoped identity and owns an optional canonical
  `Club` association.
- `MATCH.home_team_id`, `MATCH.away_team_id`, `MATCH.winner_team_id`, and
  `LINEUP.team_id` references `TEAM`, while `TEAM.federated_club_id`
  references `FEDERATED_CLUB`, which may reference `CLUB`.

Therefore, the prompt's instruction to redirect references from old
`Team` records to a new `Club` is not type-compatible and would destroy
the match natural key, lineup history, and the ability to retain different
teams from one club in a season. The processor must leave those foreign keys
unchanged, create or reuse a source-scoped `FederatedClub`, link it to one
exact-name canonical `Club`, and save replacement `Team` domain objects with
the same IDs, source, names, and seasons but with that federated association.

No `externalId` field is added to `Club` or `Team`. Federation keys stay
in source-specific import identity logic.

## Scope and safety policy

1. The processor accepts an explicit `ImportSource`, never searches by
   unscoped name, and reports results separately per source.
2. Initial automatic consolidation is enabled for FCTT and BCNESA only.
   RFETM is disabled unless a source-specific identity policy is supplied:
   its documented team keys and A/B/C team distinctions mean name matching can
   incorrectly merge distinct registrations.
3. Exact normalized-name groups are eligible automatically. Fuzzy candidates
   are eligible only when they are a mutually unique best match, pass all
   token safeguards and the configured score threshold, and contain no
   conflicting already-associated clubs. Otherwise they are warnings for
   review, not writes.
4. A run is transactional per source. A persistence error rolls back the
   source run rather than leaving a partially reassociated group. Validation
   or matching warnings do not become silent matches.
5. The operation is idempotent: a second run finds every registration already
   associated with its canonical club and performs no creation or reassignment.
   It does not delete orphaned clubs; deletion requires a separately audited
   migration after confirming no remaining references.

## Import model repairs

Restore the normal club-registration flow in the three existing processors:

- `tt-data-league-import/.../rfetm/process/RfetmTeamImportProcessor`
- `tt-data-league-import/.../bcnesa/process/BcnesaTeamImportProcessor`
- `tt-data-league-import/.../fctt/process/FcttTeamImportProcessor`

Each must resolve/create a source-scoped `FederatedClub` and an exact-name
canonical `Club`, then create the season registration with that federated
club. Preserve each source's existing identity rules:
BCNESA uses its narrow quoted-team-letter normalization; RFETM must continue
to use its source-specific key policy rather than replacing it with a
name-only upsert; FCTT must not interpret RFETM-shaped payload IDs as FCTT
club identity. Update the corresponding match processors only where their
team resolution must follow the restored source-specific registration
identity.

The reconciliation processor is historical-data repair and a guard against
previous spelling variation; it is not a substitute for correct import-time
identity resolution.

## Types and package design

Create the feature in a source-neutral package under
`org.cttelsamicsterrassa.data.load.shared.club`, with no dependency on JPA:

- `ClubNameMatcher`: immutable, unit-tested matching policy that returns an
  exact key and a classified comparison result rather than a bare boolean.
- `ClubNameNormalizer`: Unicode-normalizes, lowercases with `Locale.ROOT`,
  removes punctuation, collapses whitespace, and applies a small explicit
  abbreviation registry. Keep the registry source-aware and include only
  abbreviations demonstrated by fixtures; do not introduce generic
  football-club expansion rules for table-tennis data.
- `TeamToClubConsolidationProcessor`: constructor-injected
  `FederatedClubRepository`, `TeamRepository`, and `ClubRepository`; exposes
  `consolidate(ImportSource source)` and returns a
  `ClubConsolidationSummary`.
- `ClubConsolidationSummary`: immutable result containing scanned
  registrations, exact groups, accepted fuzzy groups, clubs created,
  registrations reassociated, already-correct registrations, and immutable
  warning/error details. Its concise `toString` supports runtime logging.
- Small immutable report values such as `ConsolidatedClub` and
  `ConsolidationWarning` preserve the source, canonical display name,
  registration IDs/names, matching mode, and rejection reason without leaking
  JPA entities.

Do not make this processor a match-report processor: it needs a complete
source-scoped registration inventory, not one parsed report context. Give it
an explicit runtime invocation after a source traversal, or expose a
dedicated repair command; it must never run independently once per report.

## Repository and domain changes

1. Extend `TeamRepository` with an explicit source-scoped inventory
   method, for example `findAllTeamsBySource(ImportSource source)`.
   Implement it in `TeamRepositoryJpa`, its Spring Data helper, and all
   in-memory test implementations. Avoid repeatedly querying
   `findAll...SimilarName`, which cannot discover arbitrary duplicate groups
   and is unsuitable for a complete reconciliation.
2. Add a domain factory or intent-revealing method that creates an otherwise
   identical existing `Team` with a supplied `FederatedClub` association. The
   current association is final, so do not add a public mutable setter. The
   processor uses this API and `saveTeam` to update
   `team.federated_club_id`; canonical links are persisted on the federated
   club.
3. Add exact-name canonical `Club` lookup and CRUD to its own port and adapter.
   Keep source-scoped `FederatedClub` lookup separate; never fall back to an
   unscoped federated name lookup.
4. In the JPA adapter, save the reassociated `Team` using the existing
   mappers. Keep `federated_club.club_id` nullable for legacy data until the
   migration has been performed; do not change column nullability in this
   feature unless an audited data migration is included.
5. Do not alter match or lineup foreign keys, their natural/unique
   constraints, or their mappers. Update
   `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` and the manual
   FEAT-008 migration document the canonical table and federated link.

## Matching and canonicalization algorithm

For each requested source:

1. Read all `Team` records for that source, reject and report null or
   blank names, and group the remaining entries by the exact normalized key.
2. For every exact group with more than one distinct registration, choose its
   source-scoped federated club deterministically:
   - retain a single already-associated club when all associated entries agree;
   - otherwise prefer the associated club from the earliest season and then
     the lexicographically smallest UUID as a stable tie-breaker;
   - when no entry has a club, create one with the stable representative name
     selected by earliest season, then normalized/display-name and UUID
     tie-breakers.
3. Reassociate each group member to that federated club while retaining its
   original season-specific name and ID. If the group contains multiple
   conflicting pre-existing federated club IDs, warn and skip automatic
   reassignment unless the selected source policy explicitly authorizes that
   migration. Canonical links are created or reused by exact canonical display
   name only; source-specific fuzzy/rules matching does not become cross-source
   canonical matching.
4. Build fuzzy candidates only between different exact groups in the same
   source. Require the same significant token set after known abbreviations,
   reject one-token/very-short names, require mutual best match, and calculate
   a normalized edit-distance score. Use a named, tested threshold constant
   rather than an unexplained literal.
5. Apply the same canonicalization only to accepted fuzzy pairs. Do not use
   transitive fuzzy chains (`A ~ B`, `B ~ C`) to merge `A` and `C`; every
   member must independently satisfy the canonical group's safeguards.
6. Record ambiguous candidates, conflicting associations, invalid names, and
   persistence failures in the summary and structured logs. Log only
   identifiers/names needed for operations; do not log complete source
   payloads.

## Runtime integration and reporting

Add an opt-in runtime argument such as `--consolidate-clubs` to
`tt-data-league-import-runtime/.../runtime/App`. After the selected source
traversal completes successfully, call the processor for that source and log
the returned summary. It must work with `--season`, but operate on the whole
selected source by default so cross-season club identity can be repaired.

Document the argument in `App`'s usage text. A later enhancement may accept
`--consolidate-clubs=report` for a dry-run report, but do not silently make a
destructive repair the default behavior. If dry-run is implemented now, it
must execute the same matching path and return the same proposed counts while
performing no saves.

## Tests

### Import module unit tests

Add focused JUnit 5 tests for `ClubNameNormalizer`, `ClubNameMatcher`, and
`TeamToClubConsolidationProcessor`, updating `InMemoryRepositories` to honor
the new source-scoped inventory and existing exact lookup contracts.

Cover:

1. Case, repeated whitespace, punctuation, and verified source-specific
   abbreviations collapse to one exact group.
2. Accent/special-character handling preserves meaningful tokens.
3. A permitted minor typo produces one fuzzy reassociation; short names,
   a non-mutual best match, a below-threshold score, and different significant
   tokens produce warnings and no writes.
4. Identically named records from different sources never group.
5. Multiple seasonal registrations with equivalent names retain their IDs,
   season names, and match/lineup references while receiving one federated
   association linked to one canonical `Club`.
6. Existing agreement on a club produces no duplicate `Club`; no associated
   club creates exactly one canonical one; conflicting associated clubs are
   reported and unchanged.
7. A second run has zero creations/reassignments and reports the entries as
   already correct.
8. Repaired import processors create a source-scoped club and a linked
   team registration on first import, then remain idempotent on repeat
   import.
9. Runtime argument parsing invokes consolidation only when explicitly
   requested and logs/returns the source summary after traversal.

### JPA integration tests

Extend `ImportSchemaTest` or add a focused repository test to persist a
canonical `Club`, multiple `Team` rows, a match, and lineups; then
reassociate the teams and reload them. Assert the
`federated_club.club_id` association is linked while match and lineup
references continue to target the same `Team.id`. Also test the source-scoped
inventory query.

## Validation

1. Run `mvn -pl tt-data-league-core-domain -am test` after domain-port changes.
2. Run `mvn -pl tt-data-league-core-repository-jpa -am test` after adapter or
   schema-contract changes.
3. Run `mvn -pl tt-data-league-import -am test` after processor and
   in-memory-repository changes.
4. Run `mvn test` from the repository root.

## Historical decisions (resolved for FEAT-008)

1. Source-specific abbreviation mappings remain supported only when demonstrated
   by representative
   exports? The initial registry must be evidence-based.
2. RFETM automatic consolidation remains disabled in the shared processor;
   its source-specific key-aware consolidation is invoked separately.
3. Opt-in post-import repair is sufficient for the current runtime. Both write
   and report modes are available; no destructive operation is enabled by
   default.
