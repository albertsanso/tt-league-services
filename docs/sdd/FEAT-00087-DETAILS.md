# Build Plan
Source task: **T11** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.7; gaps G6, G12; risk K10).

## Acceptance Criteria
- [x] Snapshot mode (season folder replaced on upload) is documented as the default contract
- [x] An upload with fewer published actas than the stored season folder is rejected unless an explicit override is given
- [x] A moving FCTT window with at least as many published actas is still accepted
- [x] Tests cover accepted, rejected and overridden uploads

## Baseline (verified 2026-09-29, after FEAT-00086 / commit 18c7e93)

- `POST /api/v1/administration/import/upload` (`ImportResourceController.uploadZipFile`) calls
  `ResourceUploadService.uploadAndTriggerAsyncLoad(filename, bytes)`. That method validates the file
  and runs `ResourceZipService.extractZipAndGetManifest` **synchronously**. It then hands
  `ResourceRepositoryLoaderService.loadIntoRepository(manifest)` to a background `Executor`, where
  failures are only logged (`SEVERE`). An `IllegalArgumentException` raised on the synchronous path
  becomes a `400` with `{"message": ...}`. Anything raised during the async load never reaches the
  operator.
- `loadIntoRepository` resolves each asset type to `import-<source>/<asset>/<season>` under the import
  folder setting. For every season it runs `deleteRecursively(seasonFolder)`, recreates the folder and
  moves the extracted content in (`moveSeasonContent`). This delete-and-replace is the snapshot contract
  (G6). The contract is not documented anywhere today.
- `moveSeasonContent` takes one of two paths. With an empty `files` list it moves the whole extracted
  `<season>/` folder. Otherwise it moves each listed file, stripping a leading `<season>/` and then
  relativising against `actas-json/<season>` for ACTAS or `equipos-json/` for TEAMS.
- `ResourceZipService.validateManifest` accepts exactly `source`, `seasons` and `assets` (G12). Delta
  mode, which changes the manifest, is FEAT-00090 and out of scope here.
- One acta file holds one fixture in all three sources (measured in `BcnesaMatchdaySplitter`). The
  import model (`Acta.isPublished`) treats `acta_publicada` as published unless it is literally
  `false`. RFETM and BCNESA never send the field, so all of their actas count as published. The domain
  module cannot use the import module's `Acta`, but it already depends on `jackson-databind`.
- Callers of the affected constructors and methods: `ImportResourceController` (the only production
  caller of `uploadAndTriggerAsyncLoad`), `ResourceUploadServiceTest`,
  `ResourceRepositoryLoaderServiceTest`, and Spring wiring through `@Named`/`@Inject`.
  `ImportResourceControllerTest` has no upload case yet.

## Contracts

1. **Published-acta rule** (new package-private `PublishedActaCounter` in `domain/load/service`, a final
   class built from an `ObjectMapper`). It counts a file as a published acta when it is a regular file
   whose name ends in `.json` (case-insensitive), its JSON root is an object, and `acta_publicada` is
   absent or is not the boolean `false`. This mirrors `Acta.isPublished`.
   - `int countPublished(Collection<Path> files)`
   - `int countPublishedIn(Path folder)`: walks the folder recursively. A missing folder gives `0`.

   A file that is unreadable or not valid JSON counts as **not published**. That makes rejection more
   likely for a broken incoming file and never raises the stored baseline. The import-time classifier
   still reports such files. `IOException` from walking a folder is rethrown as
   `UncheckedIOException`, so there is no silent fallback.
2. **Shared move resolution** (`ResourceRepositoryLoaderService`, behavior-preserving refactor):
   - Extract `private Path resolveSeasonFolder(Path importFolder, String source, String assetType,
     String season)` with the existing traversal guard.
   - Extract `private List<SeasonFileMove> resolveSeasonFileMoves(ImportManifest, List<String> files,
     String season, Path pathToRemove)`. `SeasonFileMove(Path source, Path relativeDestination)` is a
     private record. It covers the whole-folder case by walking regular files and keeps the existing
     "missing file" validation. `moveSeasonContent` then executes these moves.

   The shrink check counts exactly the files the move would place in the season folder, so the check
   and the load cannot disagree.
3. **Shrink check** (new public method on `ResourceRepositoryLoaderService`):
   ```java
   public void verifyPublishedActasNotShrinking(ImportManifest importManifest, boolean allowPublishedShrink)
   ```
   - It considers only the `ACTAS` asset (case-insensitive) and checks each manifest season. `TEAMS`
     and other assets are never checked.
   - `incoming` is `countPublished` over the resolved moves' source files. `stored` is
     `countPublishedIn` over the target season folder. It requires the import folder to exist, with the
     same error as `loadIntoRepository`.
   - If `incoming >= stored`, the upload is accepted. This covers the first upload (`stored == 0`), a
     re-upload of the same snapshot, and an FCTT window that moved while keeping or raising the
     published count. File names do not have to match.
   - If `incoming < stored` and there is no override, it throws `SnapshotShrinkException`. It checks
     every season first, so one exception lists all shrinking seasons. The message names the source,
     season, both counts and the override parameter. Example: `Upload rejected: FCTT 2026-2027 ACTAS
     has 70 published actas, fewer than the 76 already stored. Upload a complete season snapshot, or
     retry with allowPublishedShrink=true to replace the stored season anyway.`
   - If `incoming < stored` and the override is set, the upload is accepted and a `WARNING` naming the
     counts is logged through `java.util.logging`, as in `ResourceUploadService`.
   - The method reads only and never touches the stored folder. `loadIntoRepository` keeps its current
     signature and behavior.
4. **Exception**: new `SnapshotShrinkException extends IllegalArgumentException` in
   `domain/load/service`. It exposes an immutable `List<SeasonShrink>` with source, season, `stored`
   and `incoming`, so callers and tests can inspect it without parsing the message. Extending
   `IllegalArgumentException` keeps every existing handler correct.
5. **Upload service**: `uploadAndTriggerAsyncLoad(String filename, byte[] content)` becomes
   `uploadAndTriggerAsyncLoad(String filename, byte[] content, boolean allowPublishedShrink)`. The only
   dependents, the controller and `ResourceUploadServiceTest`, change in the same commit. The method
   calls `verifyPublishedActasNotShrinking` synchronously after extraction and **before** scheduling
   the async load, so a rejected upload never reaches the executor and the operator gets the error in
   the HTTP response.
6. **REST**: `uploadZipFile` gains `@RequestParam(value = "allowPublishedShrink", defaultValue =
   "false") boolean allowPublishedShrink`. A `SnapshotShrinkException`, caught before the
   `IllegalArgumentException` branch, maps to **`409 Conflict`** with `{"message": ...}`. All other
   responses are unchanged. The Swagger `@Operation` description mentions the parameter.
7. **Constructor**: `ResourceRepositoryLoaderService` gains an `ObjectMapper` parameter, the same bean
   that `ResourceZipService` already receives, and builds its `PublishedActaCounter` from it.

## Implementation order

1. Add `PublishedActaCounter` (contract 1) and `PublishedActaCounterTest` (`@TempDir`). Cases: a
   missing field counts, `true` counts, `false` does not, a non-object root and invalid JSON do not,
   non-`.json` files are ignored, nested folders are walked, and a missing folder gives `0`.
2. Refactor `ResourceRepositoryLoaderService` into the shared move resolution (contract 2). Existing
   `ResourceRepositoryLoaderServiceTest` cases must stay green. Add one ACTAS case with explicit
   `files` in the `actas-json/<season>/...` layout, asserting the files land at the same relative paths
   as before, to pin the behavior before adding the check.
3. Add `SnapshotShrinkException` (contract 4), the constructor change (contract 7) and
   `verifyPublishedActasNotShrinking` (contract 3). Update the three existing test constructions.
4. Add loader tests in `ResourceRepositoryLoaderServiceTest`, using temp import and extraction
   folders and a real `ObjectMapper`:
   - **accepted**: no stored season folder; incoming equal to stored; incoming greater than stored.
   - **FCTT window**: stored holds published j1–j3 (3). Incoming holds published j2–j4 (3) plus
     unpublished j5 files with other names. The upload is accepted.
   - **rejected**: stored has 3 published and incoming has 2 published plus several unpublished. The
     test checks for `SnapshotShrinkException`, its `SeasonShrink` values and the message counts, and
     that the stored folder's files are byte-identical afterwards.
   - **overridden**: the same data with `allowPublishedShrink = true` does not throw. A follow-up
     `loadIntoRepository` replaces the folder.
   - **RFETM/BCNESA**: files without `acta_publicada` count as published, so a shorter RFETM snapshot
     is rejected.
   - A TEAMS-only manifest is never checked, even when it holds fewer files.
   - With a two-season manifest where only one season shrinks, the exception lists that season only.
5. Update the upload service (contract 5). In `ResourceUploadServiceTest`, keep the existing test with
   `false` and verify `verifyPublishedActasNotShrinking(manifest, false)` runs before scheduling. Add
   two cases: a rejection throws, the executor is never called and `loadIntoRepository` never runs;
   and the override flag is passed through unchanged.
6. Update the REST controller (contract 6). Add upload cases to `ImportResourceControllerTest` in its
   existing style: the default flag is `false` and the result is `202`; `allowPublishedShrink=true` is
   forwarded; `SnapshotShrinkException` gives `409` with the message; a plain
   `IllegalArgumentException` still gives `400`.
7. Documentation: add a "ZIP import upload contract" section to `tt-data-league-api-runtime/README.md`,
   next to the existing upload-size notes. It covers:
   - the manifest shape (`source`, `seasons`, `assets.<ASSET>.files`) and the two supported file
     layouts;
   - **snapshot mode is the default and only mode**: each upload replaces the stored
     `import-<source>/<asset>/<season>` folder, so every ZIP must hold the complete season as
     currently exported, with published and unpublished actas;
   - the published-acta rule and the shrink check;
   - the `allowPublishedShrink` parameter and the `409` response;
   - that an FCTT window which drops old jornadas is accepted as long as the published count does not
     fall;
   - that delta uploads are not supported yet (FEAT-00090).

   No schema change, so `rfetm-datamodel.md` stays untouched.
8. Validation: `mvn -pl tt-data-league-api-rest -am test`, then the full `mvn test` from the root.

# Implementation Guidelines

- Affected modules: `tt-data-league-core-domain` (upload/load services and tests),
  `tt-data-league-api-rest` (controller and test), and `tt-data-league-api-runtime/README.md`. No
  JPA, schema, import-module, CLI or configuration change.
- Follow the repository and module `AGENTS.md` files. Keep lookups source-scoped and never add
  external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change, and
  module READMEs for CLI or configuration changes.
- The domain module stays free of Spring and JPA. `PublishedActaCounter` uses Jackson only, which is
  already a domain dependency, and must not import anything from `tt-data-league-import`.
- The check is synchronous, read-only and runs before the async load. It never deletes, moves or
  rewrites stored files. It compares **published** counts only: unpublished actas may come and go
  freely.
- Keep the error explicit. There is no silent acceptance of a shrinking snapshot and no automatic
  fallback to merging. The override is an explicit, per-request flag that defaults to `false`.
- Out of scope:
  - delta mode and the manifest `mode` field (FEAT-00090);
  - rollback copies or archiving of uploaded ZIPs (K10's other mitigations, also FEAT-00090);
  - a frontend checkbox for the override;
  - locking between concurrent uploads of the same season;
  - cleanup of the temp extraction folder on rejection. Validation failures already leave it behind,
    so this is not a new leak.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: —.
- 2026-09-29: Build plan written against the code after FEAT-00086 (commit 18c7e93); status `idea` →
  `planned`. Decisions:
  - The check runs on the synchronous upload path, not inside `loadIntoRepository`. The load is
    async and its failures are only logged, so a rejection there would never reach the operator.
  - The incoming count comes from the same resolved file moves the load executes, so the check and
    the load cannot disagree about which files belong to a season.
  - The rejection uses a dedicated `SnapshotShrinkException` (an `IllegalArgumentException`) mapped to
    `409 Conflict`, so clients can tell "retry with override" apart from a malformed ZIP (`400`).
  - The published rule mirrors `Acta.isPublished`, where a missing `acta_publicada` counts as
    published. Unparseable files count as not published, which is the safe direction for this check.
- Open question 1 (recommendation: keep the domain signature change): replacing the 2-argument
  `uploadAndTriggerAsyncLoad` rather than overloading it follows the root rule to update all
  dependents in the same change. There is one production caller.
- Open question 2 (recommendation: defer): the frontend (`tt-data-league-frontend`,
  `src/api/importJobs.js`) sends no override. Until a UI toggle exists, operators apply it with a
  direct API call. The 409 message tells them how.
- Open question 3 (recommendation: count published only): should the check also reject a large drop
  in *total* acta files? The analysis asks for the published count only, because unpublished counts
  legitimately fall as fixtures get played and FCTT's window moves.
- 2026-09-29: Plan approved by the user; status `planned` → `ready`. Open questions 1–3 stay as
  recommended (replace the 2-argument upload signature; defer the frontend override toggle; compare
  published counts only).
- 2026-09-29: Executed the build plan end to end; status `ready` → `in-progress` → `in-review`.
  Delivered exactly as contracted: `PublishedActaCounter`, `SnapshotShrinkException` (with
  `SeasonShrink`), `resolveSeasonFolder`/`resolveSeasonFileMoves`/`SeasonFileMove` refactor,
  `verifyPublishedActasNotShrinking`, the 3-argument `uploadAndTriggerAsyncLoad` on the synchronous
  path, the `allowPublishedShrink` request parameter mapped to `409 Conflict`, and the "ZIP import
  upload contract" section in `tt-data-league-api-runtime/README.md`. Validation discovery during
  implementation: the published rule must count `acta_publicada: true` as published (absent or
  non-boolean or true → published; boolean false → not published); an inverted first draft passed
  the no-throw tests but failed the rejection tests, and is now pinned by
  `PublishedActaCounterTest` and the shrink tests. The unused `moveDirectoryContents` helper was
  removed as part of the move-resolution refactor. Validation:
  `mvn -pl tt-data-league-core-domain -am test`, `mvn -pl tt-data-league-api-rest -am test` and the
  full `mvn test` all pass. All four acceptance criteria verified; `IOException` while resolving
  moves inside the check surfaces as `UncheckedIOException` (no silent fallback).
- 2026-09-29: Closure confirmed by the user; all four acceptance criteria verified and checked;
  status `in-review` → `done`. Registry validation passed (`feature_manager.py validate`).
