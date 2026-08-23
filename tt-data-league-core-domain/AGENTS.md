# Domain module instructions

## Scope

This module defines the core league model under `org.cttelsamicsterrassa.data.core.domain.model`. It currently contains `FederatedClub`, `FederatedPlayer`, `Season`, `Match`, `Lineup`, and `Game`.

## Design rules

- Keep this module independent of JPA, database schemas, Spring Data, and persistence annotations.
- Domain entities extend `org.albertsanso.commons.model.Entity` and use UUID identifiers.
- Preserve immutability: fields are final, constructors are private where practical, and state is exposed through getters.
- Use the existing factories (`createNew`, `of`) and fluent builders rather than adding public mutable setters or public constructors without a strong reason.
- `Season` is a validated value object: it requires non-null years and exactly one year between start and end. Preserve its `YYYY-YYYY` string representation.
- Use domain types for relationships (`Season`, `Club`, `Player`, `Match`) instead of primitive foreign-key identifiers.
- Keep optional data nullable where the current model permits it; do not replace it with silent defaults.

## Testing

Place tests under `src/test/java` with the matching package. Prefer focused JUnit 5 tests for factory behavior, builder construction, and invariant validation. Run:

```text
mvn -pl tt-data-league-core-domain -am test
```

## Changes requiring care

The relational model in the sibling repository module is a persistence specification, not a reason to add persistence annotations here. If a schema requirement cannot be represented cleanly in the current domain API, document and model the domain behavior first rather than leaking JPA concerns into these classes.
