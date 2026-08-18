# Fix club consolidation

## Goal

Make source-scoped club consolidation consume `ClubSeason.name` values and
produce one deterministic canonical `Club.name` without merging registrations
or changing match and lineup references.

The canonical results must cover the ten examples in
`.github/prompts/consolidate-unique-club-processor.prompt.md`, including:

- terminal team letters (`A`, `B`, `C`) and multi-token category/team
  suffixes (`Sen A/B`, `Vet A/B/C`);
- `ST`/`SANT` spelling variants;
- optional `DE`/`ELS` particles;
- venue or sponsor suffixes such as `LA CASSOLA`;
- sponsor-prefixed names where a reviewed alias identifies the real club;
- hyphen-wrapped terminal team letters such as `-A-` and `-B-`;
- accented Catalan names without silently accepting mojibake.

## Safety constraints

1. Consolidation is scoped by `ImportSource`; never match by unscoped name.
2. Preserve every `ClubSeason` row and UUID.
3. Update only `ClubSeason.club` using `ClubSeason.withClub`.
4. Never retarget or rewrite `MATCH` or `LINEUP` foreign keys.
5. RFETM automatic consolidation remains disabled until a key-aware policy
   distinguishes federation teams and competition registrations.
6. Unknown suffixes, malformed text, ambiguous candidates, and conflicting
   existing Club IDs must produce review warnings and no automatic merge.
7. `--consolidate-clubs=report` must perform no writes and calculate the same
   proposals as write mode.

## Design

### Name parsing and policies

Create immutable import-layer name parts containing the original display name,
folded tokens, identity tokens, canonical candidate tokens, removed qualifier
types, source policy, and confidence.

Use explicit source policies rather than unrestricted fuzzy matching:

- remove a standalone terminal `A`, `B`, or `C` only as a team designator;
- treat punctuation-wrapped terminal team letters (`-A-`, `-B-`, `-C-`) as
  equivalent team designators after controlled punctuation normalization;
- remove registered terminal category/team suffix sequences such as
  `Sen A/B` and `Vet A/B/C`, matching the category first and then the
  terminal team letter;
- expand only approved aliases such as `st` to `sant`;
- compare optional particles without deleting meaningful particles from the
  preferred display name;
- apply sponsor or venue aliases only when a curated policy proves the core;
- preserve valid UTF-8 accents and warn on mojibake or malformed input.

### Matching

Classify comparisons as exact, ruled variant, fuzzy, rejected, or review
required. Group only through explainable keys, in this order:

1. folded exact key;
2. team/category-neutral key, including punctuation-wrapped team letters;
3. approved spelling and particle-neutral key;
4. reviewed sponsor/venue-neutral key;
5. conservative fuzzy matching inside a shared rule-derived core.

Retain mutual-best, threshold, and non-transitive safeguards. Do not union
overlapping candidate groups when the policy evidence is ambiguous.

### Canonical name selection

Choose the display name separately from matching. Rank candidates by:

1. agreement with an existing associated Club;
2. fewest removed qualifiers;
3. expanded approved spellings and meaningful particles;
4. frequency in the source inventory;
5. earliest season, normalized lexical order, and ClubSeason UUID.

Never modify `ClubSeason.name`. Rename an existing Club only when all
registrations agree on the entity and the selected canonical name is
unambiguous; otherwise report the conflict.

### Import integration

Ensure RFETM, BCNESA, and FCTT import processors create source-scoped Clubs
and linked ClubSeasons before dependent match, lineup, and player processors.
RFETM match resolution must use the same `ActaTeam.name()` used during club
creation, falling back to `RfetmClubKey.name()` only when the payload has no
usable team name.

### Example 10

The inputs `TT SANT ANDREU -A-` and `TT SANT ANDREU -B-` must consolidate to
`TT SANT ANDREU`. Normalize the punctuation-wrapped terminal letters only
when they are standalone final team designators; do not remove hyphens or
letters embedded in meaningful club names.

## Implementation steps

1. Inspect fixtures and correct representative UTF-8 examples before adding
   aliases or category rules.
2. Add immutable name-parts and qualifier/rule types; refactor the normalizer
   and matcher without persistence dependencies.
3. Update consolidation grouping, canonical-name ranking, warnings, summaries,
   and deterministic/idempotent writes.
4. Restore and verify source-scoped Club/ClubSeason import wiring and align
   RFETM match lookup with imported team names.
5. Keep runtime flags opt-in and preserve report-mode write isolation.
6. Add focused unit and integration tests for all ten examples, safety cases,
   source isolation, canonical renaming, idempotency, and unchanged match or
   lineup references.

## Validation

Run:

```text
mvn -pl tt-data-league-import -am test
mvn -pl tt-data-league-core-repository-jpa -am test
mvn -pl tt-data-league-import-runtime -am test
mvn test
```

The final full reactor build must pass.
