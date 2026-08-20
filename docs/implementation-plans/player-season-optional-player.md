# PlayerSeason optional `player` implementation plan

## Goal

Make the `PlayerSeason.player` association optional in the domain API, matching
the existing `Team.club` design, and migrate all related logic without
changing registration identity or the nullable JPA `player_id` relationship.

## Confirmed behavior

- Store the association as `Optional<Player>`.
- Expose `Optional<Player> getPlayer()`.
- Keep `PlayerSeason.createNew`, `createExisting`, and `withPlayer` accepting
  nullable `Player` inputs; null values become `Optional.empty()`.
- Return `Optional<Player>` from `PlayerSeasonCreatedEvent.getPlayer()`.
- Preserve `PlayerSeason` IDs, source, name, licence, and season.
- Keep `LINEUP` and `DoublesPair` references to `PlayerSeason` unchanged.
- Do not add external IDs or alter the relational schema unless verification
  finds the existing nullable `player_id` contract is inaccurate.

## Implementation steps

1. **Domain model**
   - Change `PlayerSeason.player` to `Optional<Player>`.
   - Wrap constructor input with `Optional.ofNullable`.
   - Make `withPlayer` return `this` when the association is already the same,
     including safe handling of null to clear the association.
   - Update creation-event publication and `PlayerSeasonCreatedEvent`.

2. **Call-site migration**
   - Update the player consolidation processor’s conflict, canonical selection,
     reassociation, and reporting logic to use `Optional<Player>`.
   - Update player application handlers and any import processors that inspect
     `PlayerSeason.getPlayer()`.
   - Update in-memory repositories and focused tests to assert `isEmpty()` or
     `orElseThrow()` rather than compare nullable values.

3. **Persistence adapters**
   - Update `PlayerSeasonToPlayerSeasonJPAMapper` to map
     `playerSeason.getPlayer().map(...).orElse(null)`.
   - Keep the reverse mapper’s nullable JPA player conversion as an optional
     domain association.
   - Verify lineup and doubles-pair mappers continue to pass complete
     `PlayerSeason` objects.
   - Preserve nullable `player_id`, registration IDs, and metadata on save/reload.

4. **Tests and documentation**
   - Add domain tests for present, absent, same-association, replacement, and
     clearing behavior.
   - Extend import consolidation tests for empty associations and idempotent
     reassociation.
   - Extend JPA integration coverage for null and non-null `player_id` round
     trips and unchanged registration metadata.
   - Update `docs/rfetm-datamodel.md` only if the documented nullability differs
     from the implementation.

## Validation

Run:

```text
mvn -pl tt-data-league-core-domain -am test
mvn -pl tt-data-league-core-repository-jpa -am test
mvn -pl tt-data-league-import -am test
mvn -pl tt-data-league-import-runtime -am test
mvn test
```

The full reactor result must distinguish failures caused by this migration from
pre-existing import or consolidation test failures.
