# Team-to-federatedClub Consolidation Build Plan

## Objective

Build a deterministic import process that groups source-scoped `Team` entities
which represent the same federation club, creates one `FederatedClub` for each
approved group, and associates every member through `Team.withFederatedClub`.
The original `Team` rows and UUIDs must remain unchanged.

The process must operate only on `Team` and `FederatedClub` entities. It must
not consolidate any other entity, and every newly created `FederatedClub` must
have `club` set to `null`.

## Execution boundary

Run the process immediately after successful Traversal import and after MATCH
and LINEUP import. The process must receive a complete source-scoped inventory
of teams, even when the import command limits the files or season being
processed. It must not run after a partial or failed source traversal.

Provide two modes:

- **Write mode:** persist newly created `FederatedClub` entities and updated
  team associations.
- **Report mode:** perform the identical analysis and produce the proposed
  actions and warnings without persisting anything.

Consolidation remains opt-in. A team that has no approved match must have its
`federatedClub` association set to `null`; it must not be forced into an
unrelated club.

## Processing design

### 1. Collect and validate input

Gather each `Team`'s name, season, source, UUID, and current
`federatedClub` association. Require source scope for every lookup and
comparison. Preserve duplicate rows as separate input records so that
association changes never alter team identity.

Reject or report invalid records explicitly, including missing source or
unusable names. Do not silently select a source, season, or database when
 input is malformed.

### 2. Normalize names into rule-derived keys

Create a normalization pipeline whose output contains both a comparison key and
the display candidate used for canonical-name selection:

1. Trim leading and trailing whitespace, collapse consecutive whitespace, and
   compare case-insensitively.
2. Remove punctuation and special characters that do not affect identity,
   including separators around team qualifiers and quotation marks.
3. Normalize only reviewed common abbreviations and aliases, including `FC`,
   `SC`, and the approved `ST` to `SANT` rule.
4. Remove standalone terminal team qualifiers `A`, `B`, and `C`.
5. Remove complete terminal category/team sequences such as `Sen A/B` and
   `Vet A/B/C`, including the corresponding variants shown in the examples.
6. Apply reviewed handling for optional particles, venue suffixes, and sponsor
   prefixes.
7. Preserve valid UTF-8 accents in display names. Accent-insensitive comparison
   may be used only where it is an explicit matching rule; canonical display
   names must follow the selected source spelling policy.

Do not strip meaningful numbers or location words. For example, `2000` remains
part of `CTT DELS HORTS 2000`, and distinct locations following a shared
organization prefix remain distinct.

Unknown variants and ambiguous normalization must produce warnings and remain
unmerged unless a reviewed rule resolves them.

### 3. Match conservatively

Match only teams with the same source. Use the normalized rule-derived key for
strong matches, then apply fuzzy matching for minor typographical or spelling
variations such as `Club A` and `Clb A`.

Define and name a fuzzy-match threshold before implementation. Require mutual
best matching and reject ties or competing candidates. Apply non-transitive
safeguards: a match through an intermediate team must not merge two teams that
do not independently satisfy the matching policy.

The matching result should be an immutable, deterministic proposal containing:

- source;
- member team UUIDs;
- normalized comparison key;
- selected canonical name;
- match rule and confidence;
- warnings or rejection reasons.

Sort inputs and candidates by stable values such as source, normalized key,
original name, and UUID before matching and reporting. This prevents database
ordering from changing the result.

### 4. Select canonical names and consolidate

For every approved group, choose one deterministic canonical `FederatedClub`
name from the normalized team names. The selection policy must be explicit,
stable, and preserve meaningful UTF-8 display spelling.

Create exactly one `FederatedClub` per approved group, with the group's source
and canonical name. Set `FederatedClub.club` to `null` always. For every member
team, update only its association through `Team.withFederatedClub`; preserve
the team UUID, name, season, and all existing history and references.

Never retarget MATCH or LINEUP references. Never delete or replace the original
`Team` entities. A singleton may receive a newly created federated club only
when the matching policy explicitly treats it as an approved group; otherwise
leave its association `null`.

### 5. Ensure repeatable persistence

Write mode must be idempotent. Re-running the same complete source inventory
must not create duplicate federated clubs or produce additional association
changes. Before writing, detect an existing source/name result and verify that
it agrees with the proposal. Surface conflicts instead of choosing the first
row or overwriting an incompatible association.

Report mode must use the same collection, normalization, matching, canonical
selection, and conflict analysis as write mode. Its output must describe the
same proposed creations, associations, unchanged teams, warnings, and
rejections that write mode would perform.

### 6. Log and report outcomes

Log the start and completion of each source-scoped run, the number of teams
examined, normalization warnings, approved groups, newly created clubs,
association updates, unchanged teams, ambiguous matches, rejected matches,
existing-club reuse, and persistence conflicts.

Produce a deterministic summary report containing at least:

- source and execution mode;
- total teams collected;
- total approved federated clubs;
- total teams associated;
- total teams left with `null` association;
- warnings and unresolved variants;
- ambiguous or rejected matches;
- persistence actions or the explicit statement that no writes occurred.

Release temporary indexes, candidate collections, and other resources after
the report is produced, including when processing fails.

## Canonicalization acceptance examples

The implementation must produce the following outcomes:

| Input family | Canonical result |
| --- | --- |
| `CC SANT ANDREU`, its `A`/`B` variants, and `CC SANT ANDREU A Vet` | `CC SANT ANDREU` |
| `CETT SANT ANDREU DE LA BARCA` and `CETT ST ANDREU DE LA BARCA` | `CETT SANT ANDREU DE LA BARCA` |
| `CTT COLLBATO`, `CTT COLLBATO LA CASSOLA`, and `A`/`B` variants | `CTT COLLBATO` |
| `CTT DELS HORTS 2000` and its `A`/`B` variants | `CTT DELS HORTS 2000` |
| `CTT ELS AMICS [DE] TERRASSA` with `A`/`B`/`C` | `CTT ELS AMICS DE TERRASSA` |
| `ÀNECBLAU - TT JOVES CTDFELS` and `ÀNECBLAU - TT ELS JOVES` | `TT JOVES CTDFELS`, only through the reviewed alias/policy |
| `CTT SANT QUIRZE DEL VALLÈS - Sen A/B` and `- Vet A/B/C` | `CTT SANT QUIRZE DEL VALLÈS` |
| `CTT LA BISBAL DEL PENEDÈS A/B` | `CTT LA BISBAL DEL PENEDÈS` |
| `CTT COLÒNIA GÜELL A/B` | `CTT COLÒNIA GÜELL` |
| `TT SANT ANDREU -A-` and `-B-` | `TT SANT ANDREU` |
| `MANUFACTURAS DEPORTIVAS`, quoted `A`, and quoted `B` variants | `MANUFACTURAS DEPORTIVAS` |
| `TENNIS TAULA CASSA` and `CASSÀ` | `TENNIS TAULA CASSA` |
| `OBERENA 'A'` and `OBERENA "A"` | `OBERENA` |
| `CLUB TENNIS TAULA TRAMUNTANA FIGUERES` and quoted `A`/`B` variants | `CLUB TENNIS TAULA TRAMUNTANA FIGUERES` |

The following must remain separate because the shared prefix does not identify
the same club:

- `CLUB TENNIS TAULA BARCELONA`;
- `CLUB TENNIS TAULA TRAMUNTANA FIGUERES` and its `A`/`B` variants;
- `CLUB TENNIS TAULA TORELLÓ`;
- `CLUB TENNIS TAULA LA BISBAL`;
- `CLUB TENNIS TAULA ALTEA`;
- `CLUB TENNIS TAULA SANTISIMO SALVADOR`;
- `CLUB TENNIS TAULA OLESA`.

Likewise, the distinct teams beginning with `CLUB TENIS DE MESA`—including
SALUD Y DEPORTE, TABOR AÑAVINGO, COSLADA, VILLA DE VALDEMORO, MOS Dismac,
MAZDA JEREZ, TECNIK '87, VIGO, VICAR, ALCAZAR, and BASAURI—must not collapse
into one group.

## Verification plan

Add focused tests for:

- case, whitespace, punctuation, quotation, abbreviation, alias, qualifier,
  category suffix, particle, sponsor, venue, accent, and number handling;
- every canonicalization example and both shared-prefix separation examples;
- source isolation and rejection of unscoped matching;
- deterministic ordering, named fuzzy threshold, mutual-best matching, tie
  handling, and non-transitive safeguards;
- preservation of team UUIDs, seasons, names, MATCH references, and LINEUP
  references;
- `Team.withFederatedClub` association updates and `null` behavior;
- `FederatedClub.club == null`;
- idempotent write mode, conflict handling, and no-write report mode;
- complete-inventory behavior after a season-limited import;
- post-Traversal/MATCH/LINEUP sequencing, warnings, summary counts, and
  cleanup.

Completion requires that all acceptance examples pass, unrelated distinct clubs
remain separate, report and write modes generate identical proposals, repeated
write-mode execution is stable, and all invalid or ambiguous cases are visible
in logs and the summary report.
