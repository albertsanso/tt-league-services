# Federated-club to canonical-club consolidation build plan

## Objective

Add the final step of club consolidation: associate each accepted
source-specific `FederatedClub` with exactly one season-independent canonical
`Club`.

The process must reuse an existing canonical club when the selected canonical
display name matches, or create one when it does not. Multiple
`FederatedClub` records from `RFETM`, `BCNESA`, and `FCTT` may reference the
same `Club`, while a single `FederatedClub` must never reference more than one
canonical club.

This is an association and identity-resolution process. It must not merge or
delete `FederatedClub`, `Team`, `Match`, `Lineup`, or `PlayerSeason` records,
change their UUIDs, or redirect season-specific references.

## Current implementation to extend

- `FederatedClub` already exposes `getClub()` and immutable-style
  `withClub(Club)`.
- `ClubRepository` provides exact-name canonical lookup and persistence.
- `CanonicalClubResolver` already separates write resolution from report
  resolution.
- `FederatedClubRepository` supports source-and-name lookup and persistence,
  but the consolidation phase needs a deterministic complete-inventory read
  for the selected source.
- `TeamToClubConsolidationProcessor` creates/reuses source-scoped
  `FederatedClub` records for BCNESA and FCTT.
- `RfetmClubConsolidationProcessor` creates/reuses RFETM records from the team
  folder and contains a commented-out canonical-link implementation that must
  be replaced by the new final phase.
- The JPA schema already documents `FEDERATED_CLUB.club_id` as a nullable lazy
  many-to-one relationship with no cascade.

## Execution boundary and runtime wiring

1. Complete the selected source traversal successfully.
2. Run the existing source-specific federated-club consolidation.
3. Run the new federated-club-to-canonical-club phase over the complete
   source-scoped `FederatedClub` inventory, even when `--season` limited the
   imported files.
4. Run player consolidation afterward, if requested.

Keep the operation opt-in and preserve the existing `--consolidate-clubs`
write/report modes. The runtime should pass the same `ConsolidationMode` to
both phases and combine their summaries, so report mode performs matching,
canonical-club resolution, conflict detection, and reporting without any
database writes.

Do not run the final phase after a failed traversal or after a partial source
inventory. Keep RFETM's required team-folder validation in its existing
source-specific phase; the canonical-link phase should consume the resulting
federated-club inventory rather than parse source files again.

## Processing design

### 1. Collect and validate the source inventory

- Add a repository-port operation, or an equivalent existing-port composition,
  to load all `FederatedClub` records for one `ImportSource` in stable order.
- Require a non-null source and handle null/blank names explicitly as warnings
  or errors according to the existing consolidation summary conventions.
- Preserve already-linked records as input. A record linked to one canonical
  club must not be silently moved to another club merely because a later name
  comparison produces a different candidate.
- Detect and report duplicate or conflicting source/name records rather than
  selecting the first database row.
- Keep every lookup source-scoped while selecting federated-club candidates.
  Canonical `Club` lookup is global only after a deterministic canonical
  display name has been selected, because `Club.name` is globally unique.

### 2. Derive deterministic matching keys

Reuse `ClubNameNormalizer`, `ClubNameMatcher`, and the reviewed rules already
used by team-to-federated-club consolidation. Do not create a second,
inconsistent normalization policy.

For each source-scoped federated club, retain:

- original UUID, source, and display name;
- normalized comparison key;
- selected canonical display name;
- matching rule and confidence;
- warnings, rejected candidates, and existing-link conflicts.

Use exact normalized-key grouping first, followed by the existing conservative
fuzzy policy where appropriate. Fuzzy matching must be source-scoped, mutual
best matching, deterministic, tie-safe, and non-transitive. Do not fuzzy-match
unrelated clubs solely because they share a generic prefix. Cross-source
reuse happens only through an exact canonical display-name lookup after
resolution; source differences must never collapse two federated records into
one federated record.

### 3. Resolve the canonical club

For each approved group:

1. Select one deterministic canonical display name using the existing
   preferred-display-name policy, preserving meaningful UTF-8 spelling.
2. Call `CanonicalClubResolver.resolveOrCreate` in write mode, or
   `findOrCreateForReport` in report mode.
3. Ensure a newly created canonical `Club` is persisted before any
   `FederatedClub` references it.
4. For each member, retain the existing link when it points to the resolved
   UUID; otherwise create an updated value with `FederatedClub.withClub`.
5. Persist only the updated federated-club association in write mode.

If members of one proposed group already point to different canonical clubs,
report a conflict and leave that group unchanged. If an existing link conflicts
with the proposed canonical name, do not overwrite it automatically. A
singleton may be linked only when the matching policy accepts it; otherwise
leave it unchanged and report why.

Never modify the `FederatedClub` source or source-provided name to perform this
association. Never add external identifiers to either club entity.

### 4. Make persistence repeatable

Write mode must be idempotent:

- re-running the same complete source inventory creates no duplicate
  canonical clubs;
- already-correct federated-club links produce no additional writes;
- exact canonical-name reuse is validated against the globally unique
  `CLUB.name` constraint;
- duplicate canonical rows or incompatible existing links fail visibly or are
  reported as conflicts, rather than being resolved by ordering.

No delete, cascade, or replacement operation is permitted. The existing
nullable `club_id` relationship remains sufficient; no schema change is needed
unless implementation discovery identifies a mismatch with the documented
schema contract.

## Reporting and observability

Extend or compose `ClubConsolidationSummary` so the final result distinguishes
at least:

- federated clubs examined;
- approved groups and rejected/ambiguous groups;
- canonical clubs created;
- existing canonical clubs reused;
- federated-club links created or reassociated;
- links already correct;
- records left unlinked;
- normalization warnings and persistence conflicts;
- source and execution mode;
- explicit report-mode no-write status.

Emit start and completion logs for the source-scoped final phase. Include the
member federated-club UUIDs, canonical-club UUID/name, matching rule, and
warnings in the deterministic report, while avoiding generated files or
sensitive configuration. Release temporary candidate indexes and collections
after successful or failed processing.

## Implementation surfaces

### Import module

- Add the final consolidation processor and immutable proposal/result types
  under the existing shared club consolidation package.
- Reuse the current normalization, matching, resolver, warning, mode, and
  summary abstractions.
- Add the complete-inventory repository port needed by the processor and
  update in-memory repositories used by tests.
- Remove the obsolete commented canonical-link block from the RFETM processor
  and invoke the final phase after RFETM federated-club creation.

### Domain module

- Preserve the current `Club`, `FederatedClub`, and repository-port APIs where
  possible.
- If a complete source inventory cannot be expressed through the existing
  `FederatedClubRepository`, add the narrowest source-scoped read method
  without introducing Spring, JPA, or mutable setters into the domain.

### JPA repository module

- Implement the inventory query with explicit source filtering and stable
  ordering.
- Preserve the documented lazy nullable many-to-one mapping and no-cascade
  behavior.
- Ensure both mappers round-trip `FederatedClub.club` correctly.
- Update
  `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if the
  persistence contract changes; otherwise document that the existing
  `club_id` schema is reused.

### Import runtime module

- Wire the new processor once after source-specific federated-club
  consolidation and before player consolidation.
- Preserve independent club/player modes and explicit argument validation.
- Update the runtime README only if the CLI behavior, sequencing, or report
  output contract changes.

## Verification plan

Add focused JUnit 5 tests covering:

- exact normalized grouping and reviewed fuzzy matching;
- source isolation and cross-source canonical-club reuse by exact canonical
  name;
- generic-prefix separation, ambiguous matches, ties, rejected candidates,
  and non-transitive safeguards;
- deterministic ordering and canonical display-name selection;
- creation and reuse of canonical clubs;
- association of multiple federated clubs to one canonical club;
- preservation of federated-club UUID/source/name and all team, match, lineup,
  and player-season references;
- pre-existing correct links and conflicting links;
- idempotent write mode;
- report mode producing the same proposals and counts without writes;
- complete-inventory behavior after a season-limited import;
- RFETM, BCNESA, and FCTT sequencing and source-specific behavior;
- summary counts, warnings, no orphaned accepted records, and cleanup.

Run the focused import/runtime and persistence-module tests, followed by the
full reactor test command:

```text
mvn -pl tt-data-league-import-runtime -am test
mvn test
```

Completion requires that every accepted federated club has exactly one
canonical association, rejected or conflicting records remain unchanged and
visible in the report, multiple source records can safely share a canonical
club, report and write modes produce identical proposals, and repeated writes
are stable.
