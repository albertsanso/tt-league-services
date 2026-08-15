# Summary

Builds a tree directory navigator for loading the table tennis matches results reports from FCTT.

# Description

The starting directory is `C:\git\fctt-extract\resources\actas-json` and the nested folders structure is as follows:
`/[season]/[league-competition]/[group]/jornada_[day]_partido_[match_number].json`

Where:
- `[season]` is the season of the match (e.g., 2023-2024)
- `[league-competition]` is the league or competition name (e.g., "tercera nacional")
- `[group]` is the group within the league or competition
- `[day]` is the match day (e.g., 1, 2, 3, ...)
- `[match_number]` is the match number within the match day

The report file name is **opaque**: treat it as `jornada_[day]_partido_[match_number].json`,
where `[day]` and `[match_number]` are arbitrary identifiers that carry no meaning for the import. 

File names must never be parsed for the match day or for team identifiers — the match day is read from the JSON payload (`jornada`),
and the teams involved are determined from the payload as well.

The goal is to give the collected information to a list of components that will process the information.
The different components will process the collected information for different aspects, basically store the collected information into a data model in a database.
The list of components that process the information must be a parameter for the traverse/navigation component.

# Procedure

1. Start traversing from the root directory `C:\git\fctt-extract\resources\actas-json`.
2. Recursively traverse the directory structure to identify all seasons, league-competitions, groups, and match report files.
3. While traversing, build a context collecting the information for the season, league-competition, group, and the corresponding match report file names. Parse each report and read the match day from the JSON payload (`jornada`); do not derive it from the file name.
4. When a match report file is found, pass the collected context and the file path to the list of components for processing. Reports whose payload carries no match day cannot be placed in a round and are skipped.

# Architecture

All import logic will be located into a new module called `tt-data-league-import`, which will be a dependency 
of the `tt-data-league-import-runtime` module and included into `tt-data-league-core` root module.

- The traverse/navigation component and the processing components will be designed to work together.
- The traverse/navigation component will handle the directory traversal and context collection, while the processing components will handle the specific logic for storing or manipulating the collected information.
- The traverse/navigation component  must be coded in `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.fctt.traverse` package.
- The processing components will be coded in the `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.fctt.process` package.
- All the components will be designed to be easily extendable, allowing for new processing components to be added in the future without modifying the traverse/navigation component.
- Apply best practices for code organization, naming conventions, and documentation to ensure maintainability and readability of the codebase.

# Implementation details

The basic idea is the JPA data model will be the common persistence model for all the data, both FCTT and BCNESA, and the processing components
will be responsible for mapping the parsed data into the JPA data model and persisting it into the database.

- Current `org.cttelsamicsterrassa.data.load.fctt.parse`  package in `tt-data-league-import` module will be used for parsing the match report files, and the parsed data will be passed to the processing components for further handling.
- The traverse/navigation component will be implemented as a class named `FcttActasDirectoryNavigator`, which will provide methods for traversing the directory structure and collecting the necessary context information.

# Implementation Plan file

Create an implementation plan file named `fctt-actas-directory-navigator-implementation-plan.md` in the `docs/implementation-plans` directory, which will contain the following sections:
- Overview: A brief overview of the implementation plan and its purpose.
- Directory Structure: A detailed description of the directory structure and the expected file naming conventions.
- Traverse/Navigation Component: A detailed description of the traverse/navigation component, including its responsibilities, methods, and how it will interact with the processing components.
- Processing Components: A detailed description of the processing components, including their responsibilities, methods, and how they will handle the parsed data.
- Implementation Steps: A step-by-step guide on how to implement the traverse/navigation component and the processing components, including any necessary setup or configuration.
- Testing: A description of the testing strategy for the traverse/navigation component and the processing components, including any test cases or scenarios that should be covered.
- Conclusion: A summary of the implementation plan and any final thoughts or considerations for the implementation.



