# Summary

Builds a tree directory navigator for loading the table tennis matches results reports from RFETM.

# Description

The starting directory is `C:\git\rfetm-extract-2\resources\actas-json` and the nested folders structure is as follows:
/[season]/[league-competition]/[day]/[sex]/acta.json

Where:
- `[season]` is the season of the match (e.g., 2023-2024)
- `[league-competition]` is the league or competition name (e.g., "super-divisio", "divisio-honor", "primera-divisio", "segona-divisio")
- `[day]` is the match day (e.g., "1", "2", "3")
- `[sex]` is the gender of the teams (e.g., "masculino" for male, "femenino" for female)

The report file name is **opaque**: treat it as `acta.json` or `acta_[match_id].json`, where `[match_id]`
is an arbitrary identifier that carries no meaning for the import. File names must never be parsed for
team identifiers — the teams involved in a match are read from the JSON payload instead.

Within the payload, a team is identified in one of two ways, and the import must accept both:

- by `equipos.local.id` / `equipos.visitante.id` when the export carries them (16,106 of 20,619 reports);
- by `equipos.local.nombre` / `equipos.visitante.nombre` when it does not (the remaining 4,513, among
  them **every one of the 4,017 files of `2025-2026`**).

A name is only an identifier **within a season and a competition**. A club's A, B and C teams normally
share one name and are told apart by the division they play in, so a name key must be scoped to
`(season, league-competition, sex)` — never to the name alone. Measured against the reports that do
carry ids: scoping by season alone would merge 249 distinct teams, by season and sex 178, and by
season, competition and sex **zero**.

The goal is to give the collected information to a list of components that will process the information.
The different components will process the collected information for different aspects, basically store the collected information into a data model in a database.
The list of components that process the information must be a parameter for the traverse/navigation component.

# Procedure

1. Start traversing from the root directory `C:\git\rfetm-extract-2\resources\actas-json`.
2. Recursively traverse the directory structure to identify all seasons, league-competitions, days, and sexes.
3. While traversing, build a context collecting the information for the season, league-competition, day, sex, and the corresponding match report file names. Parse each report and identify the two teams from the JSON payload — by `equipos.*.id` where present, otherwise by `equipos.*.nombre` scoped to the season and competition; do not derive either from the file name.
4. When a match report file is found, pass the collected context and the file path to the list of components for processing. Only a report that identifies a side by **neither** id nor name cannot be attributed to a club and is skipped (64 files in the whole export, whose `equipos` block is entirely null).

# Architecture
- The traverse/navigation component and the processing components will be designed to work together. 
- The traverse/navigation component will handle the directory traversal and context collection, while the processing components will handle the specific logic for storing or manipulating the collected information.
- The traverse/navigation component  must be coded in `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.traverse` package.
- The processing components will be coded in the `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.process` package.
- All the components will be designed to be easily extendable, allowing for new processing components to be added in the future without modifying the traverse/navigation component.
- Apply best practices for code organization, naming conventions, and documentation to ensure maintainability and readability of the codebase.
