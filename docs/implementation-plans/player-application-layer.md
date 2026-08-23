# Player application layer implementation plan

## Scope

Add the canonical Player application surface under
`org.cttelsamicsterrassa.data.core.application.player`, mirroring the existing
Club application structure while using the Player and PlayerSeason repository
ports and preserving source-scoped identity.

## Planned packages and types

### `player.create`

- `CreatePlayerCommand`: command carrying occurred time, command id, optional
  canonical player UUID, and canonical display name. Canonical `Player` has no
  source field.
- `CreatePlayerCommandHandler`: reject an existing exact canonical name;
  otherwise create with `Player.createNew` and save through `PlayerRepository`.
- `CreateFederatedPlayerCommandHandler`: resolve or create the exact-name
  canonical `Player`, then create the source-scoped `FederatedPlayer` while
  retaining its source-provided name.
- `CreatePlayerSeasonCommand`: command carrying source, player-season name,
  licence, parent `FederatedPlayer`, and `Season`.
- `CreatePlayerSeasonCommandHandler`: resolve or receive the parent
  `FederatedPlayer`,
  reject an existing `(source, license, season)` registration, create with
  `PlayerSeason.createNew`, and save through `PlayerSeasonRepository`.

The `PlayerSeason` parent should be represented by the domain
`FederatedPlayer` relationship, not an external id. The federated relationship
may be absent for historical or partially imported registrations. Canonical
linking belongs on `FederatedPlayer`, with source-scoped resolution at import
boundaries.

### `player.update`

- `ModifyPlayerNameCommand` and `ModifyPlayerNameCommandHandler`, following
  the Club equivalent and saving the modified aggregate.
- `ModifyPlayerSeasonNameCommand` and
  `ModifyPlayerSeasonNameCommandHandler`, following the same pattern for a
  `PlayerSeason`.

Handlers should preserve the existing response convention: successful
mutation returns `DomainCommandResponse.successResponse`, and a missing
aggregate returns a non-throwing “not found” success response as the Club
implementation currently does.

### `player.delete`

- `DeletePlayerCommand` and `DeletePlayerCommandHandler`.
- `DeletePlayerSeasonCommand` and `DeletePlayerSeasonCommandHandler`.

Each handler publishes the domain deletion event by calling `delete()` before
calling the matching repository deletion operation. The PlayerSeason repository
port currently exposes save/find only, so deletion requires adding an explicit
`deletePlayerSeasonById(UUID)` port method or deferring the PlayerSeason delete
handler until that port exists; do not silently substitute save or an
unscoped operation.

### `player.find`

- `FindPlayerByIdQuery` / handler -> `findPlayerById`.
- `FindPlayerByNameQuery` / handler -> exact canonical `Player` name lookup.
- `FindFederatedPlayerBySourceAndNameQuery` / handler ->
  `findFederatedPlayerBySourceAndName`.
- `FindPlayerSeasonByIdQuery` / handler -> `findPlayerSeasonById`.
- `FindPlayerSeasonByLicenseAndSeasonQuery` / handler ->
  `findPlayerSeasonByLicenseAndSeason(source, license, season)`.

The source-aware federated query is required for federation-safe imports.
Canonical name lookup is intentionally unscoped because canonical names are
globally unique; importer-facing federated lookups must remain source-scoped.

## Validation and tests

Add focused JUnit 5 tests for command payloads and each handler’s success,
duplicate, missing, source-scoped lookup, mutation, event-triggering, and save/
delete behavior. First verify the existing module baseline, then run:

`mvn -pl tt-data-league-core-domain -am test`

Finally run the repository-required full build:

`mvn test`

## Open design decision before implementation

The Club reference has no Team command/update/delete handlers and its
name queries are not source-scoped, while PlayerSeason has a source-scoped
licence natural key and no delete repository operation. Implementation should
confirm whether to:

1. mirror only the existing Club application surface exactly, or
2. provide the complete aggregate surface above and extend
   `PlayerSeasonRepository` with deletion.
