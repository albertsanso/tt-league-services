# Summary

Team entities are created for each unique club in the system.
However, there are instances where multiple Team entities exist for the same club, leading to redundancy and potential confusion.
This prompt aims to consolidate these duplicate Team entities into a single entity per unique club, ensuring data integrity and simplifying management.

# Description

In module `tt-data-league-import`, the `Team` entity is designed to represent a club's participation in a specific season.
However, due to various data import processes or manual entries, there can be multiple `Team` entities for the same club, which can lead to inconsistencies and difficulties in managing club data.

Create a new processor that identifies and consolidates duplicate `Team` entities for the same club. The processor should:
1. Identify all `Team` entities associated with the same club.
2. Create a new Club instance that consolidates the data from the duplicate `Team` entities.
3. The matching criteria is based in the Team's name. Apply an algorithm to determine if two Team names refer to the same club (e.g., ignoring case, whitespace, and common abbreviations).
4. Update all references to the old `Team` entities to point to the new consolidated `Club` instance.

# Matching algorithm

The matching algorithm should consider the following factors to determine if two `Team` names refer to the same club:
1. Case Insensitivity: The comparison should ignore case differences (e.g., "Club A" and "club a" should be considered the same).
2. Whitespace Normalization: Leading and trailing whitespace should be ignored, and multiple consecutive spaces should be treated as a single space (e.g., "Club A" and "Club   A" should be considered the same).
3. Common Abbreviations: Recognize and normalize common abbreviations (e.g., "FC" for "Football Club", "SC" for "Sports Club", etc.) to ensure that variations in naming conventions do not lead to false negatives in identifying duplicates.
4. Special Characters: Ignore special characters and punctuation that do not affect the core identity of the club name (e.g., "Club-A" and "Club A" should be considered the same).
5. Fuzzy Matching: Implement a fuzzy matching algorithm to account for minor typographical errors or variations in spelling (e.g., "Club A" and "Clb A" should be considered the same).

# Acceptance Criteria
1. The processor should successfully identify duplicate `Team` entities based on the defined matching criteria.
2. A new consolidated `Club` instance should be created for each unique club identified.
3. All associations from the `Team` entities should be updated to the canonical `Club` instance.
4. The processor should handle edge cases, such as clubs with similar names but different identities, ensuring that only true duplicates are consolidated.
5. The processor should log its actions, including the number of duplicates found, the names of the consolidated clubs, and any issues encountered during the process.
6. The processor should be idempotent, meaning that running it multiple times should not create additional duplicates or alter the data incorrectly after the first run.
7. The processor should be tested with a variety of scenarios, including clubs with different naming conventions, to ensure robustness and accuracy in identifying duplicates.
8. The processor should provide a summary report of the consolidation process, including the number of duplicates consolidated, the names of the new consolidated clubs, and any errors or warnings encountered.
9. The processor should be designed to be easily extendable for future enhancements, such as adding new matching criteria or handling additional data fields in the `Team` entity.
10. The processor should be implemented in a way that it can be easily integrated into the existing `tt-data-league-import` module, following the module's coding standards and practices.

# Canonicalization requirements

The processor must consume source-scoped `Team.name` values and choose
one deterministic canonical `Club.name`. Preserve every Team row and
UUID, update associations only through `Team.withClub`, and never
retarget MATCH or LINEUP references.

Use explicit source-aware rules. Standalone terminal `A`, `B`, and `C` are
team qualifiers. Category/team N-gram suffixes such as `Sen A/B` and
`Vet A/B/C` must be removed as a complete terminal sequence. Support only
reviewed aliases such as `ST` to `SANT`, optional particles, venue suffixes,
and sponsor prefixes. Unknown or ambiguous variants must produce warnings,
not automatic merges. Preserve valid UTF-8 accents in canonical display names.

Matching must remain source-scoped and conservative: use rule-derived keys,
mutual-best fuzzy matching with a named threshold, and non-transitive
safeguards. RFETM automatic consolidation remains disabled until a key-aware
policy distinguishes federation teams and competition registrations. Write
mode must be idempotent, while report mode must perform no writes and produce
the same proposed actions.

# Canonical name examples

**Example 1**

Input: `CC SANT ANDREU`, `CC SANT ANDREU A`, `CC SANT ANDREU B`,
`CC SANT ANDREU A Vet`

Output: `CC SANT ANDREU`

**Example 2**

Input: `CETT SANT ANDREU DE LA BARCA`, its `A` and `B` variants, and
`CETT ST ANDREU DE LA BARCA`

Output: `CETT SANT ANDREU DE LA BARCA`

**Example 3**

Input: `CTT COLLBATO`, `CTT COLLBATO LA CASSOLA`, and its `A`/`B` variants

Output: `CTT COLLBATO`

**Example 4**

Input: `CTT DELS HORTS 2000`, `CTT DELS HORTS 2000 A/B`

Output: `CTT DELS HORTS 2000`; `2000` remains significant.

**Example 5**

Input: the `CTT ELS AMICS [DE] TERRASSA` variants with team letters `A/B/C`

Output: `CTT ELS AMICS DE TERRASSA`

**Example 6**

Input: `ÀNECBLAU - TT JOVES CTDFELS` and `ÀNECBLAU - TT ELS JOVES`

Output: `TT JOVES CTDFELS`, only through a reviewed alias/policy.

**Example 7**

Input: `CTT SANT QUIRZE DEL VALLÈS - Sen A/B` and
`CTT SANT QUIRZE DEL VALLÈS - Vet A/B/C`

Output: `CTT SANT QUIRZE DEL VALLÈS`; remove the category/team N-gram suffix.

**Example 8**

Input: `CTT LA BISBAL DEL PENEDÈS A/B`

Output: `CTT LA BISBAL DEL PENEDÈS`

**Example 9**

Input: `CTT COLÒNIA GÜELL A/B`

Output: `CTT COLÒNIA GÜELL`

**Example 10**

Input: [
"TT SANT ANDREU -A-",
"TT SANT ANDREU -B-"
]
Output: `TT SANT ANDREU`

# Additional acceptance criteria

Add focused JUnit 5 coverage for all nine examples, exact and ruled-variant
matching, N-gram suffixes, accents, ambiguity, conflicting Club IDs,
idempotency, report-only behavior, source isolation, and unchanged
match/lineup references. The module and full Maven reactor test suites must
pass.
