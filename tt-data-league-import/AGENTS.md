# Import module instructions

## Scope

This module parses RFETM and BCNESA source data and translates it into domain entities through import processors. It must remain independent of JPA, Spring Data repositories, and runtime deployment concerns.

## Module boundaries

- Depend on `tt-data-league-core-domain` for domain types and repository ports.
- Do not import classes from `tt-data-league-core-repository-jpa` or `tt-data-league-import-runtime`.
- Keep persistence decisions in repository implementations and keep runtime wiring in the runtime module.
- Use source-scoped repository lookups whenever data can originate from more than one federation.

## Import identity

- `Club` and `Player` do not contain `externalId` properties.
- Preserve source-system identifiers such as RFETM team keys and player licences in the appropriate import or season-registration flow.
- Do not deduplicate clubs or players using an unscoped name when a source, season, competition, licence, or team key is required to distinguish records.
- Do not silently normalize invalid or incomplete source data. Log and skip malformed records using the existing navigator and processor conventions.

## Code conventions

- Use Java 21 and the existing package roots.
- Keep processors small and explicit about parsing, lookup, creation, and registration steps.
- Preserve source-specific behavior; do not assume RFETM and BCNESA payloads have identical fields or identity rules.
- Prefer existing domain factories, builders, repository ports, and parser value objects over duplicate logic.
- Catch only the exceptions the surrounding navigator contract expects. Do not add broad catches that turn failures into successful imports.

## Testing and validation

Add focused JUnit 5 tests for parser edge cases, source-specific identity resolution, duplicate handling, malformed records, and processor behavior. Use the in-memory repository implementations in tests where appropriate.

Run:

```text
mvn -pl tt-data-league-import -am test
```

Run the full reactor with `mvn test` before completing changes that affect shared domain APIs or import behavior.
