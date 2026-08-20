# Import runtime module instructions

## Mission and scope

This module is the executable Spring Boot runtime for league-data imports. It
wires source navigators, import processors, repository adapters, database
configuration, and the command-line entry point. Keep reusable parsing,
matching, domain behavior, and persistence mapping in their owning modules.

The module README is the operational reference for setup and launch examples:
`tt-data-league-import-runtime/README.md`.

## Boundaries and dependency direction

- Depend on the public APIs of the domain, JPA repository, and import modules.
- Keep source parsing and import business rules in `tt-data-league-import`.
- Keep persistence mapping and database behavior in
  `tt-data-league-core-repository-jpa`.
- Keep the domain module free of Spring Boot, JPA, and runtime configuration.
- Do not duplicate processors, repository implementations, or domain rules here.
- Runtime sequencing and argument parsing belong here; source-specific
  algorithms belong in the import module.

## Runtime contract

- Use Java 21 and UTF-8.
- Supported sources are `rfetm`, `bcnesa`, and `fctt`; the default source is
  `rfetm`.
- `--actas-folder=<path>` is required. Fail clearly when it is missing or
  invalid; do not silently select another folder or source.
- `--season=<YYYY-YYYY>` limits traversal to one season. Without it, traverse
  all available seasons.
- Import traversal must complete successfully before optional consolidation
  runs.
- Club consolidation runs once after traversal, against the complete
  source-scoped inventory, even when traversal was limited with `--season`.
- Player consolidation runs after club consolidation when requested.
- Consolidation is opt-in. Bare `--consolidate-clubs` and
  `--consolidate-players` use write mode; `=report` uses the same matching path
  without persistence writes.
- Keep club and player consolidation modes independent and preserve explicit
  validation of unsupported argument values.

## Configuration and operational safety

- Keep datasource and Spring configuration explicit and environment-driven.
- Use the existing `DB_TTLEAGUEDATA_JDBC_URL`,
  `DB_TTLEAGUEDATA_CREDENTIAL_USERNAME`, and
  `DB_TTLEAGUEDATA_CREDENTIAL_PASSWORD` environment variables.
- Never commit credentials, tokens, passwords, generated output, or
  environment-specific secrets.
- Do not weaken report mode into a write operation or make consolidation
  destructive by default.
- Preserve executable Spring Boot packaging and the configured actuator
  health/metrics endpoints unless the task explicitly changes the operational
  contract.
- Surface configuration, traversal, and persistence failures explicitly. Do
  not catch broad exceptions or convert failures into successful imports.

## Change workflow

Before editing, inspect the nearest module guidance, `README.md`, `pom.xml`,
`App`, `ImportRuntimeArguments`, and affected runner/configuration classes.
Search for existing public APIs and tests before adding new runtime behavior.
Make surgical changes, preserve unrelated worktree changes, and update the
README when CLI arguments, configuration, launch behavior, or operational
assumptions change.

When changing runtime wiring or argument behavior, add focused JUnit 5 tests
using the existing test infrastructure. Do not introduce a new framework,
formatter, linter, or build tool for a runtime change.

## Validation

Run the focused reactor test command from the repository root:

```text
mvn -pl tt-data-league-import-runtime -am test
```

Run the full reactor before completing changes that affect module wiring,
shared APIs, persistence configuration, or import sequencing:

```text
mvn test
```

Do not add generated `target/` content to source changes. Before concluding,
check the final diff for accidental files, secrets, formatting errors, and
changes outside the requested scope.
