# Summary

Build an import process that consolidates multiple Team entities into a single Federated Club entity. 

# Description

The process should identify duplicate Team entities based on their names, apply a matching algorithm to determine if they refer to the same club, and create a new consolidated Club instance.

Team properties:
- `name`: The name of the team, which may contain variations or abbreviations.
- `season`: The season in which the team is active.
- `source`: That means the federation identifier, which is used to scope the matching process and ensure that only teams from the same federation are considered for consolidation.
- `federatedClub`: A reference to the new consolidated Club instance that will be created for duplicate Team entities.

Federated Club properties:
- `name`: The canonical name of the club, derived from the consolidated Team names.
- `source`: The federation identifier, which should match the source of the consolidated Team entities.

All references from Team to the new Federated Club instance should be updated, while preserving the original Team entities and their UUIDs.

The process should handle edge cases, log its actions, and provide a summary report of the consolidation process.

# Execution workflow

This process must be executed just after the Traversal import process, and after the MATCH and LINEUP import processes. The workflow should include the following steps:
1. **Data Collection**: Gather all Team entities from the import process, including their names, seasons, and sources.
2. **Normalization**: Normalize the Team names by applying case insensitivity, whitespace normalization, and removing common abbreviations and special characters.
3. **Matching**: Apply the matching algorithm to identify duplicate Team entities that refer to the same Federated Club.
4. **Consolidation**: Create a new Federated Club instance for each group of duplicate Team entities, using the canonical name derived from the normalized Team names.
5. **Updating References**: Update the `federatedClub` reference in each Team entity to point to the new Federated Club instance, while preserving the original Team entities and their UUIDs.
6. **Logging and Reporting**: Log the actions taken during the consolidation process, including any warnings for ambiguous or unknown variants. Generate a summary report of the consolidation process, including the number of consolidated clubs and any issues encountered.
7. **Cleanup**: Perform any necessary cleanup actions, such as removing temporary data structures or releasing resources used during the consolidation process.

# Matching algorithm

The matching algorithm should consider the following factors to determine if two Team names refer to the same Federated Club:
1. Case Insensitivity: The comparison should ignore case differences (e.g., "Club A" and "club a" should be considered the same).
2. Whitespace Normalization: Leading and trailing whitespace should be ignored, and multiple consecutive spaces should be treated as a single space (e.g., "Club A" and "Club   A" should be considered the same).
3. Common Abbreviations: Recognize and normalize common abbreviations (e.g., "FC" for "Football Club", "SC" for "Sports Club", etc.) to ensure that variations in naming conventions do not lead to false negatives in identifying duplicates.
4. Special Characters: Ignore special characters and punctuation that do not affect the core identity of the club name (e.g., "Club-A" and "Club A" should be considered the same).
5. Fuzzy Matching: Implement a fuzzy matching algorithm to account for minor typographical errors or variations in spelling (e.g., "Club A" and "Clb A" should be considered the same).

# Matching Criteria (Team names to Federated Club names) - Canonicalization requirements

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


** Example 11
Input: [
"CTT SANT QUIRZE DEL VALLÈS - Sen A",
"CTT ST QUIRZE DEL VALLÈS - Sen C",
"CTT SANT QUIRZE DEL VALLÈS - Vet A",
"CTT SANT QUIRZE DEL VALLÈS - Sen B",
"CTT ST QUIRZE DEL VALLÈS - Vet D A",
"CTT ST QUIRZE DEL VALLÈS - Vet C",
"CTT ST QUIRZE DEL VALLÈS - Vet B",
"CTT ST QUIRZE DEL VALLÈS - Vet A",
"CTT ST QUIRZE DEL VALLÈS - Sen B",
"CTT ST QUIRZE DEL VALLÈS - Vet E B",
"CTT SANT QUIRZE DEL VALLÈS - Vet B",
"CTT SANT QUIRZE DEL VALLÈS - Vet C",
"CTT ST QUIRZE DEL VALLÈS - Sen D"
]
Output: [
"CTT SANT QUIRZE DEL VALLÈS"
]

** Example 12
Input: [
"MANUFACTURAS DEPORTIVAS",
"MANUFACTURAS DEPORTIVAS",
"MANUFACTURAS DEPORTIVAS 'A'",
"MANUFACTURAS DEPORTIVAS 'B'"
]
Output: [
"MANUFACTURAS DEPORTIVAS"
]

** Example 13
Input: [
"TENNIS TAULA CASSA",
"TENNIS TAULA CASSA",
"TENNIS TAULA CASSÀ"
]
Output: [
"TENNIS TAULA CASSA"
]
Comments: Normalize the club name by removing accents and ensuring consistent spelling.

** Example 14
Input: [
"OBERENA 'A'",
"OBERENA "A""
]
Output: [
"OBERENA"
]
Comments: Remove quotes and ensure consistent naming for the club.

** Example 15
Input: [
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'"
]
Output: [
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES"
]
Comments: Normalize the club name by removing quotes and ensuring consistent naming for the club.

** Example 16
Input: [
"CLUB TENNIS TAULA BARCELONA",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
"CLUB TENNIS TAULA TORELLÓ",
"CLUB TENNIS TAULA LA BISBAL",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'",
"CLUB TENNIS TAULA ALTEA",
"CLUB TENNIS TAULA SANTISIMO SALVADOR",
"CLUB TENNIS TAULA OLESA"
]
Those are all different clubs, so the output should be the same as the input, with no consolidation.
But the consolidation is currently applied and resolves to a single club name, which is incorrect. The expected output should be the same as the input list, with no consolidation applied.

The wrong unique club name is:
Output: [
"CLUB TENNIS TAULA ALTEA"
]

The correct output should be:

Club "CLUB TENNIS TAULA BARCELONA", for Team "CLUB TENNIS TAULA BARCELONA"
Club "CLUB TENNIS TAULA TRAMUNTANA FIGUERES", for Teams "CLUB TENNIS TAULA TRAMUNTANA FIGUERES", "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'", "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'"
Club "CLUB TENNIS TAULA TORELLÓ", for Team "CLUB TENNIS TAULA TORELLÓ"
Club "CLUB TENNIS TAULA LA BISBAL", for Team "CLUB TENNIS TAULA LA BISBAL"
Club "CLUB TENNIS TAULA ALTEA", for Team "CLUB TENNIS TAULA ALTEA"
Club "CLUB TENNIS TAULA SANTISIMO SALVADOR", for Team "CLUB TENNIS TAULA SANTISIMO SALVADOR"
Club "CLUB TENNIS TAULA OLESA", for Team "CLUB TENNIS TAULA OLESA"

And another example of a wrong consolidation is:
Input: [
"CLUB TENIS DE MESA SALUD Y DEPORTE",
"CLUB TENIS DE MESA TABOR AÑAVINGO",
"CLUB TENIS DE MESA COSLADA",
"CLUB TENIS DE MESA VILLA DE VALDEMORO",
"CLUB TENIS DE MESA MOS Dismac",
"CLUB TENIS DE MESA MAZDA JEREZ",
"CLUB TENIS DE MESA TECNIK '87",
"CLUB TENIS DE MESA VIGO",
"CLUB TENIS DE MESA VICAR",
"CLUB TENIS DE MESA ALCAZAR",
"CLUB TENIS DE MESA BASAURI"
]
The wrong unique club name is:
Output: [
"CLUB TENIS DE MESA ALCAZAR"
]
The correct output should be:
Club "CLUB TENIS DE MESA SALUD Y DEPORTE", for Team "CLUB TENIS DE MESA SALUD Y DEPORTE"
Club "CLUB TENIS DE MESA TABOR AÑAVINGO", for Team "CLUB TENIS DE MESA TABOR AÑAVINGO"
Club "CLUB TENIS DE MESA COSLADA", for Team "CLUB TENIS DE MESA COSLADA"
Club "CLUB TENIS DE MESA VILLA DE VALDEMORO", for Team "CLUB TENIS DE MESA VILLA DE VALDEMORO"
Club "CLUB TENIS DE MESA MOS Dismac", for Team "CLUB TENIS DE MESA MOS Dismac"
Club "CLUB TENIS DE MESA MAZDA JEREZ", for Team "CLUB TENIS DE MESA MAZDA JEREZ"
Club "CLUB TENIS DE MESA TECNIK '87", for Team "CLUB TENIS DE MESA TECNIK '87"
Club "CLUB TENIS DE MESA VIGO", for Team "CLUB TENIS DE MESA VIGO"
Club "CLUB TENIS DE MESA VICAR", for Team "CLUB TENIS DE MESA VICAR"
Club "CLUB TENIS DE MESA ALCAZAR", for Team "CLUB TENIS DE MESA ALCAZAR"
Club "CLUB TENIS DE MESA BASAURI", for Team "CLUB TENIS DE MESA BASAURI"
