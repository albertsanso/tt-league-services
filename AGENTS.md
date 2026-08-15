# Project instructions

## Overview

This repository is a Maven multi-module Java project for table-tennis league data. The root project is the `tt-data-league-services` aggregator with these modules:

- `tt-data-league-core-domain`: framework-light domain model.
- `tt-data-league-core-repository-jpa`: persistence adapter and relational-model specification.
- `tt-data-league-import`: source parsing and import processors.
- `tt-data-league-import-runtime`: executable Spring Boot runtime wiring the import and persistence modules.

Keep the domain independent from persistence and Spring concerns. Changes that cross the boundary should be explicit through mappers or repository ports. Preserve the domain module's public API unless the dependent import and runtime modules are updated in the same change.

Club and Player domain/JPA entities are identified by UUID, source, and name; they do not contain `externalId` properties. Source-system identifiers such as RFETM team keys and player licences belong to import or season-registration identity handling, not to Club or Player entity state. Do not reintroduce external-id fields into those entities to solve lookup or persistence problems.

## Build and validation

- Use Java 21 for the root project and all current modules. Preserve this declaration unless the compatibility policy is intentionally changed.
- Run the full build from the repository root with `mvn test`.
- Build a single module with `mvn -pl <module> -am test`.
- Do not add generated `target/` content to source changes.

The repository has focused JUnit 5 tests in the JPA and import modules, but no repository-wide formatter or linter. Add focused JUnit 5 tests alongside behavior changes rather than introducing a new test framework. A change is not complete while the full `mvn test` build is failing; update affected import, persistence, and in-memory test implementations together.

## Code conventions

- Use the existing package root `org.cttelsamicsterrassa.data.core`.
- Follow the existing Java style: four-space indentation, explicit imports, and simple immutable classes.
- Preserve UTF-8 and Java 21-compatible syntax in shared code.
- Avoid unrelated dependency or plugin changes in the parent POM.
- Keep validation and failure behavior explicit; do not silently normalize invalid league data.
- Keep federation/source scoping explicit for all import lookups. Never resolve clubs or players by an unscoped name when source identity matters.
- Treat `docs/rfetm-datamodel.md` as the persistence schema contract and update it whenever JPA columns, relationships, cascades, or constraints change.

## Module boundaries

Read the nearest module `AGENTS.md` before changing module code. The more specific file supplements these instructions:

- `tt-data-league-core-domain/AGENTS.md`
- `tt-data-league-core-repository-jpa/AGENTS.md`

The import and runtime modules currently have no module-specific guidance; follow this file and the existing package/module boundaries there.
