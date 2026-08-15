# Summary

Builds a tree directory navigator for loading the table tennis matches results reports from RFETM.

# Description

The starting directory is `C:\git\bcnesa-extract-2\resources\actas-json` and the nested folders structure is as follows:
/[season]/[league-competition]/[group]/[phase]/acta.json

Where:
- `[season]` is the season of the match (e.g., 2023-2024)
- `[league-competition]` is the league or competition name (e.g., "Preferent", "Primera", "Segona")
- `[group]` is the group within the league or competition (e.g., "G1", "G2", "G3")
- `[phase]` is the phase of the competition (e.g., "1a Fase")

The report file name is **opaque**: treat it as `acta.json` or `acta_[match_id].json`, where `[match_id]`
is an arbitrary identifier that carries no meaning for the import. File names must never be parsed for
the match day or for team identifiers — the match day is read from the JSON payload (`jornada`), and the
teams involved are determined from the payload as well.

The goal is to give the collected information to a list of components that will process the information.
The different components will process the collected information for different aspects, basically store the collected information into a data model in a database.
The list of components that process the information must be a parameter for the traverse/navigation component.

# Procedure

1. Start traversing from the root directory `C:\git\bcnesa-extract-2\resources\actas-json`.
2. Recursively traverse the directory structure to identify all seasons, league-competitions, groups, and phases.
3. While traversing, build a context collecting the information for the season, league-competition, group, phase, and the corresponding match report file names. Parse each report and read the match day from the JSON payload (`jornada`); do not derive it from the file name.
4. When a match report file is found, pass the collected context and the file path to the list of components for processing. Reports whose payload carries no match day cannot be placed in a round and are skipped.

# Architecture

All import logic will be located into a new module called `tt-data-league-import`, which will be a dependency of the 
`tt-data-league-import-runtime` module and included into `tt-data-league-core` root module.

- The traverse/navigation component and the processing components will be designed to work together.
- The traverse/navigation component will handle the directory traversal and context collection, while the processing components will handle the specific logic for storing or manipulating the collected information.
- The traverse/navigation component  must be coded in `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.traverse` package.
- The processing components will be coded in the `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.process` package.
- All the components will be designed to be easily extendable, allowing for new processing components to be added in the future without modifying the traverse/navigation component.
- Apply best practices for code organization, naming conventions, and documentation to ensure maintainability and readability of the codebase.


# Implementation details

The basic idea is the JPA data model will be the common persistence model for all the data, both RFETM and BCNESA, and the processing components 
will be responsible for mapping the parsed data into the JPA data model and persisting it into the database.

- Current `org.cttelsamicsterrassa.data.load.parse`  package in `tt-data-league-import` module will be reused for parsing the match report files, and the parsed data will be passed to the processing components for further handling.
- The traverse/navigation component will be implemented as a class named `BcnesaActasDirectoryNavigator`, which will provide methods for traversing the directory structure and collecting the necessary context information.

