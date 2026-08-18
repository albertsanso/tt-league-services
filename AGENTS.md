# Project instructions

## Mission and architecture

This repository is a Maven multi-module Java project for table-tennis league
data. The root aggregator is `tt-data-league-services` and currently contains:

- `tt-data-league-core-domain`: framework-light domain model and repository ports.
- `tt-data-league-core-repository-jpa`: JPA persistence adapter and relational
  model specification.
- `tt-data-league-import`: source parsing, import processors, and
  source-specific reconciliation rules.
- `tt-data-league-import-runtime`: executable Spring Boot import runtime.
- `tt-data-league-api-rest`, `tt-data-league-api-runtime`, and
  `tt-data-league-api-graphql`: API and API runtime modules.

Keep dependencies directed inward: domain code must not depend on Spring or
JPA; import code must not depend on persistence adapters; and runtime modules
must wire existing public APIs rather than duplicate business logic. Changes
that cross a boundary must use explicit domain ports, mappers, or adapters.
Preserve public APIs unless all affected dependents are updated in the same
change.

Before changing a module, read the nearest `AGENTS.md`; its instructions
supplement this file:

- `tt-data-league-core-domain/AGENTS.md`
- `tt-data-league-core-repository-jpa/AGENTS.md`
- `tt-data-league-import/AGENTS.md`
- `tt-data-league-import-runtime/AGENTS.md`
- `docs/sdd/AGENTS.md` for feature-planning and SDD registry work.

`docs/sdd/` is a documentation/planning module rather than an application
code module. When working there, follow its feature registry, status workflow,
ID/link, ordering, and agent-boundary rules. For application code referenced
by an SDD plan, also follow the nearest application-module `AGENTS.md`.

## Identity and data integrity

`Club` and `Player` domain/JPA entities are identified by UUID, source, and
name. They do not contain `externalId` properties. Source-system identifiers
such as RFETM team keys and player licences belong to import or
season-registration identity handling, not to Club or Player entity state.
Never add external-id fields to those entities to solve lookup or persistence
problems.

Keep federation/source scoping explicit for every import lookup. Never resolve
clubs or players by an unscoped name when source identity matters. Preserve
season-specific `ClubSeason` and `PlayerSeason` identity, match history, and
lineup references when consolidating records.

Club consolidation is opt-in and source-scoped. It runs after a successful
source traversal, scans the complete source inventory even when `--season`
limits the imported files, and supports write and report modes. Report mode
must perform the same matching analysis without persistence writes. Do not
make consolidation destructive or default-enabled.

## Configuration and secrets

- Use Java 21, UTF-8, and the existing package root
  `org.cttelsamicsterrassa.data.core`.
- Keep datasource, Spring, and runtime configuration explicit and
  environment-driven.
- Never commit credentials, tokens, passwords, generated output, or
  environment-specific secrets.
- Fail clearly on invalid or missing required configuration. Do not silently
  select another database, source, season, or default for malformed data.
- Avoid unrelated dependency or plugin changes in the parent POM.

## Code and documentation conventions

- Use four-space indentation, explicit imports, and Java 21-compatible syntax.
- Prefer focused immutable domain values and existing factories/builders.
- Reuse existing repository ports, helpers, and matching policies before adding
  new abstractions.
- Keep validation and failure behavior explicit; do not add broad catches,
  silent fallbacks, or success-shaped error handling.
- Treat `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` as the
  persistence schema contract. Update it whenever JPA columns, relationships,
  cascades, constraints, or table behavior change.
- Update module README documentation when CLI arguments, configuration,
  launch behavior, or operational assumptions change. Do not create planning
  or tracking Markdown files in the repository.
- Treat `docs/sdd/FEATURES.md` as the authoritative feature registry. Keep
  feature details, statuses, acceptance criteria, links, and completion
  ordering synchronized when SDD maintenance is explicitly requested.

## Change workflow

Inspect the relevant implementation, tests, repository ports, and module
guidance before editing. Search for existing APIs and analogous behavior
before introducing new helpers. Make surgical changes, preserve unrelated
worktree changes, and do not use destructive Git commands or rewrite history.

For behavior changes, add focused JUnit 5 tests alongside the affected module
and update in-memory, import, persistence, and runtime implementations
together when their contracts change. Do not introduce a new test framework,
formatter, linter, or build tool unless the task requires it.

## Build and validation

Run the full reactor from the repository root:

```text
mvn test
```

Run a module and its required upstream modules:

```text
mvn -pl <module> -am test
```

Useful focused commands include:

```text
mvn -pl tt-data-league-core-domain -am test
mvn -pl tt-data-league-core-repository-jpa -am test
mvn -pl tt-data-league-import -am test
mvn -pl tt-data-league-import-runtime -am test
```

Do not add generated `target/` content to source changes. Before concluding,
review the final diff for accidental files, secrets, formatting errors, and
changes outside the requested scope. A change is incomplete while the full
`mvn test` build is failing unless the failure is explicitly reported.
