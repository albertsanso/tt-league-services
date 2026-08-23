# PlayerSeason optional `player` implementation plan

## Goal

Make the `PlayerSeason.federatedPlayer` association optional in the domain API,
matching the existing `Team.club` design, and migrate all related logic without
changing registration identity or the nullable JPA `federated_player_id`
relationship.

## Confirmed behavior

- Store the association as `Optional<FederatedPlayer>`.
- Expose `Optional<FederatedPlayer> getFederatedPlayer()`.
- Keep `PlayerSeason.createNew`, `createExisting`, and
  `withFederatedPlayer` accepting nullable `FederatedPlayer` inputs; null
  values become `Optional.empty()`.
- Return `Optional<FederatedPlayer>` from `PlayerSeasonCreatedEvent.getFederatedPlayer()`.
- Preserve `PlayerSeason` IDs, source, name, licence, and season.
- Keep `LINEUP` and `DoublesPair` references to `PlayerSeason` unchanged.
- Do not add external IDs or alter the relational schema unless verification
  finds the existing nullable `federated_player_id` contract is inaccurate.

## Implementation steps

1. **Domain model**
   - Change `PlayerSeason.federatedPlayer` to `Optional<FederatedPlayer>`.
   - Wrap constructor input with `Optional.ofNullable`.
   - Make `withFederatedPlayer` return `this` when the association is already the same,
     including safe handling of null to clear the association.
   - Update creation-event publication and `PlayerSeasonCreatedEvent`.

2. **Call-site migration**
   - Update the player consolidation processor’s conflict, canonical selection,
     reassociation, and reporting logic to use `Optional<FederatedPlayer>`.
   - Update player application handlers and any import processors that inspect
     `PlayerSeason.getFederatedPlayer()`.
   - Update in-memory repositories and focused tests to assert `isEmpty()` or
     `orElseThrow()` rather than compare nullable values.

3. **Persistence adapters**
   - Update `PlayerSeasonToPlayerSeasonJPAMapper` to map
     `playerSeason.getFederatedPlayer().map(...).orElse(null)`.
   - Keep the reverse mapper’s nullable JPA federated-player conversion as an
     optional domain association.
   - Verify lineup and doubles-pair mappers continue to pass complete
     `PlayerSeason` objects.
   - Preserve nullable `federated_player_id`, registration IDs, and metadata
     on save/reload. The separate `federated_player.player_id` column links
     to canonical `Player` and must not replace this registration reference.

4. **Tests and documentation**
   - Add domain tests for present, absent, same-association, replacement, and
     clearing behavior.
   - Extend import consolidation tests for empty associations and idempotent
     reassociation.
   - Extend JPA integration coverage for null and non-null `federated_player_id` round
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
