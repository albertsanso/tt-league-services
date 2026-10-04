# Build Plan
Source task: **T14** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.7; gaps G6, G12; requirement R8; risk K10; open question 1).

## Acceptance Criteria
- [x] The manifest accepts an optional mode of snapshot or delta, defaulting to snapshot
- [x] Delta mode merges files into the season folder without deleting existing ones
- [x] A rollback copy of the season folder is kept for delta uploads
- [x] README documents the manifest field

## Baseline (verified 2026-09-29, after FEAT-00089 / commit d76c18c)

- `ResourceZipService.validateManifest` rejects any manifest that does not hold exactly `source`,
  `seasons` and `assets` (`manifest.size() != 3`), so a `mode` key is a `400` today (G12). It builds
  `new ImportManifest(source, seasons, assets, extractionFolder)`.
- `ImportManifest` is a 4-component record in `domain/resource/model`. Constructor callers:
  `ResourceZipService` (production) and the tests `ResourceUploadServiceTest` (4) and
  `ResourceRepositoryLoaderServiceTest` (6, including the `manifest(...)` helper). Every other user only
  reads accessors.
- `ResourceRepositoryLoaderService.loadIntoRepository` (async, failures only logged `SEVERE` by
  `ResourceUploadService`) does, per asset and season: `resolveSeasonFolder`, `resolveSeasonFileMoves`,
  `deleteRecursively(seasonFolder)`, `createDirectory`, `moveSeasonContent` (`Files.move` without
  `REPLACE_EXISTING`). This is the snapshot contract (FEAT-00087). ACTAS then marks the season's
  `ImportResource` pending again.
- `verifyPublishedActasNotShrinking(manifest, allowPublishedShrink)` runs synchronously before the
  async load and compares `countPublished(incoming move sources)` with `countPublishedIn(stored season
  folder)`. It assumes replacement, so under a merge it would wrongly reject every delta that holds
  fewer published actas than the whole stored season.
- `SnapshotShrinkException` (`409` in `ImportResourceController`) builds a snapshot-worded message
  ("Upload a complete season snapshot, ...").
- The navigators list season folders directly under `import-<source>/actas/` and warn on and skip
  names that do not match the season pattern (`FcttActasDirectoryNavigator` L145-149; RFETM/BCNESA to
  be confirmed in step 1). A rollback copy therefore must **not** live under `import-<source>/`.
- Re-running an import over the merged folder is idempotent (R6, FEAT-00081 lifecycle), so a delta
  load can keep re-marking the whole season resource pending.

## Contracts

1. **`UploadMode`** (new enum in `domain/resource/model`): `SNAPSHOT`, `DELTA`, with
   `static UploadMode fromManifestValue(String value)` accepting exactly `"snapshot"` or `"delta"`
   (lowercase, as specified in the analysis) and throwing `IllegalArgumentException("manifest.json mode
   must be one of snapshot, delta")` otherwise.
2. **`ImportManifest`** gains a trailing `UploadMode mode` component (`Objects.requireNonNull`). A
   secondary 4-argument constructor delegates with `UploadMode.SNAPSHOT`, so existing test
   construction and any reader stay source-compatible; `ResourceZipService` uses the canonical
   5-argument constructor.
3. **Manifest validation** (`ResourceZipService.validateManifest`): `source`, `seasons`, `assets` are
   required; `mode` is optional; any other key is rejected. New message: `"manifest.json must contain
   source, seasons, assets and optionally mode"`. `mode`, when present, must be textual and pass
   `UploadMode.fromManifestValue`; absent → `SNAPSHOT`. A non-textual or unknown mode is a `400`
   (plain `IllegalArgumentException`), never a silent fallback to snapshot.
4. **Loader, delta branch** (`loadIntoRepository`), per asset and season when `mode == DELTA`:
   1. resolve the season folder and moves exactly as today (shared `resolveSeasonFileMoves`);
   2. `keepRollbackCopy(importFolder, source, assetType, season, seasonFolder)`: when the season folder
      exists, copy it recursively to
      `<importFolder>/upload-rollback/<source-lower>/<asset-lower>/<season>/`, first deleting the
      previous rollback copy at that path (one rollback copy per source/asset/season: the state
      before the most recent delta). The path gets the same traversal guard as `resolveSeasonFolder`.
      With no stored season folder there is nothing to roll back to and no copy is made (logged at
      `INFO`);
   3. `Files.createDirectories(seasonFolder)` and move each file with `REPLACE_EXISTING`: files with
      the same relative path are overwritten, every other stored file is kept. Nothing is deleted from
      the season folder;
   4. ACTAS then calls `createResourcesAndStartProcessing` as in snapshot mode.

   If the rollback copy fails, the `IOException` aborts the load before any file is moved (existing
   `IllegalArgumentException("Unable to store extracted ZIP content", ...)` path). Snapshot mode is
   unchanged and keeps no rollback copy. Private helpers: `mergeSeasonContent(List<SeasonFileMove>,
   Path)`, `keepRollbackCopy(...)`, `copyRecursively(Path, Path)`; `resolveRollbackFolder(...)` beside
   `resolveSeasonFolder`.
5. **Shrink check under delta** (`verifyPublishedActasNotShrinking`, signature unchanged, reads
   `importManifest.mode()`):
   - `SNAPSHOT`: unchanged (`incoming` = published count of the moves).
   - `DELTA`: `incoming` = the **projected merged** published count = published stored files whose
     relative path is not a move destination + published incoming files. A pure addition therefore
     never shrinks; only an overwrite of a published acta by an unpublished or invalid copy can. The
     comparison, the override and the warning log stay the same.
   - A new private helper lists the stored season folder's regular files (missing folder → empty)
     and feeds `PublishedActaCounter.countPublished(Collection<Path>)`; `PublishedActaCounter` itself
     is unchanged.
6. **`SnapshotShrinkException`** gains the `UploadMode` in its constructor
   (`SnapshotShrinkException(UploadMode mode, List<SeasonShrink>)`, getter `getMode()`). The message
   keeps the counts; the advice line depends on the mode. SNAPSHOT: unchanged text. DELTA:
   `"... would have N published actas after merging, fewer than the M already stored (the delta
   overwrites published actas with unpublished or invalid copies). Remove those files from the ZIP, or
   retry with allowPublishedShrink=true to merge anyway."` The only constructor caller is the loader;
   existing tests assert on the list and the counts and are updated in the same change.
7. **Upload service and REST**: no signature change. `ImportResourceController`'s Swagger
   `@Operation` description mentions the manifest `mode` and that delta uploads merge and keep a
   rollback copy. Responses (`202`, `400`, `409`) are unchanged.

## Implementation order

1. **Verify baseline.** Confirm that the RFETM and BCNESA navigators, the resource/inventory scans
   (`ResourceCreationService`, club consolidation's full-source inventory) and the import-runtime CLI
   never list the import folder root, so `upload-rollback/` beside `import-<source>/` is invisible to
   imports. Record the result in `# Notes`; if something does list the root, move the rollback root
   and update contract 4 before continuing.
2. **Domain model** (contracts 1-2). Add `UploadMode` and extend `ImportManifest`. Add
   `UploadModeTest` (both values, uppercase `"DELTA"`, blank and unknown rejected) and an
   `ImportManifest` check that the 4-argument constructor yields `SNAPSHOT` and a null mode throws.
3. **Manifest validation** (contract 3). In `ResourceZipServiceTest`: no `mode` → `SNAPSHOT`;
   `"snapshot"` and `"delta"` parsed; `"DELTA"`, `"merge"`, `1` and `null` rejected; an unknown extra
   key still rejected; the existing multi-asset and version-one cases stay green.
4. **Delta load** (contract 4). Add to `ResourceRepositoryLoaderServiceTest` (temp import and extraction
   folders, as the existing tests do):
   - delta into an existing ACTAS season: files not in the ZIP are kept byte-identical, new files are
     added, a same-path file is overwritten with the incoming content;
   - the rollback folder holds exactly the pre-upload season content, and a second delta replaces it
     with the state before that second delta;
   - delta with no stored season: the season folder is created, no rollback folder is written;
   - both file layouts (empty `files` list and explicit `actas-json/<season>/...`) and a TEAMS asset
     merge the same way;
   - the ACTAS import resource is re-marked pending, as in snapshot mode;
   - regression: a snapshot upload still deletes files absent from the ZIP and writes no rollback
     folder;
   - a rollback-copy failure (for example a regular file placed where the rollback folder must be
     created) throws and leaves the season folder untouched.
5. **Shrink check under delta** (contracts 5-6). Tests: a delta that only adds unpublished files is
   accepted even though its own published count is below the stored count; a delta that overwrites
   one published acta with an unpublished copy is rejected with a DELTA-mode `SnapshotShrinkException`
   whose `SeasonShrink.incoming` is the projected count and whose message carries the delta advice;
   the same upload with `allowPublishedShrink = true` is accepted and the merge runs; existing
   snapshot shrink tests keep passing with the updated constructor.
6. **Upload service.** Extend `ResourceUploadServiceTest` with one delta manifest case showing the
   check runs before scheduling and the manifest (with its mode) is handed to the loader unchanged.
7. **REST.** Update the Swagger description (contract 7). Add one `ImportResourceControllerTest` case
   asserting that a DELTA `SnapshotShrinkException` still maps to `409` with its message.
8. **Documentation.** In `tt-data-league-api-runtime/README.md`, "ZIP import upload contract":
   - the manifest now has three required keys and an optional `mode` (`snapshot` default, `delta`),
     with a delta example;
   - replace "Snapshot mode is the default and only mode" and the "Delta uploads ... not supported yet"
     paragraph with the two-mode contract: snapshot replaces; delta merges, overwrites same-path files,
     never deletes, and keeps one rollback copy at
     `<import folder>/upload-rollback/<source>/<asset>/<season>/` (the season as it was before the
     latest delta), with manual restore steps (stop imports, replace the season folder with the
     rollback copy, then start an import for the resource);
   - how the shrink check projects the merged count in delta mode.
   No schema change: `rfetm-datamodel.md` stays untouched. Check whether the import-runtime README
   describes the upload manifest and cross-reference if it does.
9. **Validate.** `mvn -pl tt-data-league-api-rest -am test`, then the full `mvn test` from the root;
   review the diff.

# Implementation Guidelines

- Affected modules: `tt-data-league-core-domain` (`UploadMode`, `ImportManifest`, `ResourceZipService`,
  `ResourceRepositoryLoaderService`, `SnapshotShrinkException` and their tests),
  `tt-data-league-api-rest` (Swagger description and one controller test) and
  `tt-data-league-api-runtime/README.md`. No JPA, schema, import-module, CLI or configuration change.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- The domain module stays free of Spring and JPA; the new code uses `java.nio.file` and Jackson only.
- Snapshot remains the default and its behavior (delete-and-replace, shrink check, `409`) is
  unchanged. Delta is opt-in per upload through the manifest; there is no server-side default switch.
- Delta never deletes from the season folder. The only deletion it performs is replacing the previous
  rollback copy, which lives outside every folder an import reads.
- Keep failures explicit: an invalid `mode` is a `400`; a failed rollback copy aborts the load before
  any file moves; there is no fallback from delta to snapshot or the other way round.
- The shrink check stays synchronous and read-only; in delta mode it compares the projected merged
  count so pure additions are never rejected.
- Out of scope:
  - a restore/rollback endpoint or UI (restore is a documented manual step);
  - a rollback copy for snapshot uploads and archiving of uploaded ZIPs (K10's remaining mitigation);
  - deleting files through a delta (tombstones); a delta cannot remove a stored acta;
  - frontend support for choosing the mode (`tt-data-league-frontend`);
  - locking between concurrent uploads of the same season;
  - making the async load's failures visible to the operator (pre-existing, FEAT-00087 baseline).

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P2, size M, Slice 4: hardening. Depends on: FEAT-00087 (T11).
- 2026-09-29: Build plan written against commit d76c18c (after FEAT-00087 to FEAT-00089); status
  `idea` → `planned`. Decisions:
  - The mode lives in the manifest (analysis 4.7), not in a request parameter, so the ZIP describes
    itself and automated uploads (FEAT-00093) need no extra flag.
  - Mode values are exact lowercase `snapshot` / `delta`; anything else is rejected rather than
    guessed.
  - Delta overwrites same-path files. This is what lets a later export of a previously unpublished acta
    (now published) replace the stale copy.
  - One rollback copy per source/asset/season, stored at `<import folder>/upload-rollback/...`, outside
    `import-<source>/` because the FCTT navigator warns on any non-season folder there. Keeping only
    the latest copy bounds disk use.
  - The shrink check is kept in delta mode but projects the merged folder, so it only catches deltas
    that overwrite published actas with unpublished or invalid copies.
  - `ImportManifest` keeps a 4-argument convenience constructor (defaults to snapshot), so the public
    API stays source-compatible.
- Open question 1 (recommendation: latest copy only): keep only the most recent rollback copy, or keep
  timestamped copies with a retention limit?
- Open question 2 (recommendation: defer): should snapshot uploads also keep a rollback copy? The
  analysis (K10) lists it as a mitigation, but the acceptance criteria scope it to delta.
- Open question 3 (recommendation: no): should delta support removing a stored acta (for example a
  tombstone list in the manifest)? Deleting would contradict R8; a full snapshot is the way to drop
  files.
- Open question 4 (analysis open question 1): if every upload stays a full-season snapshot, delta mode
  is optional hardening. Priority stays low until an extractor or FEAT-00093 needs it.
- 2026-09-29: Implemented. Baseline step 1 confirmed: `RfetmActasDirectoryNavigator` /
  `BcnseaActasDirectoryNavigator` / `FcttActasDirectoryNavigator` walk the resolved
  `import-<source>/actas/<season>` folder, RFETM club consolidation walks the
  `import-rfetm/teams` setting, and no repository code lists the import folder root, so
  `upload-rollback/` beside `import-<source>/` stays invisible to imports.
  - `UploadMode` (exact lowercase `snapshot` / `delta`, anything else rejected) and the trailing
    `ImportManifest.mode` component with the snapshot-defaulting 4-argument constructor landed in
    `domain/resource/model`.
  - `ResourceZipService.validateManifest` now requires `source`, `seasons`, `assets`, allows optional
    `mode`, rejects any other key and any non-textual or unknown mode with a plain
    `IllegalArgumentException` (`400`).
  - `ResourceRepositoryLoaderService` gained the delta branch (`keepRollbackCopy`,
    `mergeSeasonContent` with `REPLACE_EXISTING`, `resolveRollbackFolder`, `copyRecursively`); the
    rollback copy is one per source/asset/season at
    `<import folder>/upload-rollback/<source>/<asset>/<season>/`, deleted-and-recreated before each
    delta, and a copy failure aborts before any move.
  - `verifyPublishedActasNotShrinking` reads `mode()`; delta compares the stored count with the
    projected merged count (kept published stored files plus published incoming files).
  - `SnapshotShrinkException` carries the `UploadMode` and a mode-specific advice line.
  - Tests: `UploadModeTest`, `ImportManifestTest`, extra `ResourceZipServiceTest`,
    `ResourceRepositoryLoaderServiceTest` (merge, rollback replacement, no-stored-season, both
    layouts plus TEAMS, re-pending, snapshot regression, rollback failure, delta shrink cases),
    `ResourceUploadServiceTest` and `ImportResourceControllerTest` (delta `409`).
  - Docs: `tt-data-league-api-runtime/README.md` "ZIP import upload contract" rewritten for the two
    modes, the rollback path and manual restore, and the projected delta shrink check; the
    `@Operation` Swagger text updated. Import-runtime README does not describe the upload manifest, so
    no cross-reference was needed. No schema change (`rfetm-datamodel.md` untouched).
  - Validation: `mvn -pl tt-data-league-api-rest -am test` green, full `mvn test` green from the root.
