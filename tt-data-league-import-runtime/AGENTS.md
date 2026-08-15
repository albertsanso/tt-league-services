# Import runtime module instructions

## Scope

This module is the executable Spring Boot runtime. It wires the import processors, repository adapters, database configuration, and application entry point; it should not contain reusable parsing or domain logic.

## Module boundaries

- Depend on the import, domain, and JPA repository modules through their public APIs.
- Keep source parsing and business rules in `tt-data-league-import`.
- Keep persistence mapping and database behavior in `tt-data-league-core-repository-jpa`.
- Keep the domain module free of Spring Boot, JPA, and runtime configuration concerns.
- Do not duplicate processor or repository implementations in this module.

## Configuration and operations

- Use Java 21 and UTF-8.
- Keep database, datasource, and Spring configuration explicit and environment-driven.
- Never commit credentials, tokens, passwords, or environment-specific secrets.
- Fail clearly when required runtime configuration is missing; do not silently fall back to a different database or import source.
- Preserve the executable Spring Boot packaging configured in the module POM.

## Testing and validation

Add focused Spring Boot or integration tests only when runtime wiring or configuration behavior changes. Reuse the existing JPA and import test infrastructure rather than introducing another framework.

Run:

```text
mvn -pl tt-data-league-import-runtime -am test
```

Run the full reactor with `mvn test` before completing changes that affect module wiring, shared APIs, or persistence configuration.
