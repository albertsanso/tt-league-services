# Build Plan
**Hash algorithm (shared contract).** Take every ZIP entry except directory entries and the root `manifest.json`.
Use each entry's name exactly as stored in the ZIP (UTF-8, `/` separators, no normalisation) and sort the names by
their UTF-8 bytes (the same order as Python `sorted(str)`; in Java compare `getBytes(UTF_8)` with
`Arrays.compareUnsigned`, not `String.compareTo`). For each name append `<name>\n<lowercase hex SHA-256 of the
entry's uncompressed bytes>\n` to one UTF-8 buffer. `contentSha256` is the lowercase hex SHA-256 of that buffer.
The hash does not depend on ZIP timestamps, compression or entry order. Both sides hash the ZIP entries, never an
extracted folder, so file-system path handling cannot change the result.

**Ship order.** Steps 1-4 (Java) go in before or with steps 5-9 (Python). Today `ResourceZipService` rejects any
manifest key it does not know (`rejectsAnUnknownExtraKey`), so an older platform would reject the new ZIPs.

1. **Domain values (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/resource/model/`).**
   - `MatchCounts(long expected, long withResult, long pending)`: rejects negative values and requires
     `withResult + pending == expected` (`IllegalArgumentException`).
   - `ManifestProvenance(Optional<String> runId, Optional<String> generator, Optional<String> generatorVersion,
     Optional<String> contentSha256, Optional<MatchCounts> matchCounts)`: null components are rejected,
     `static final ManifestProvenance EMPTY` has every component empty.
   - `ImportManifest`: new last component `ManifestProvenance provenance` (`Objects.requireNonNull`). The existing
     4-argument constructor and a new 5-argument constructor `(source, seasons, assets, extractionFolder, mode)` both
     delegate with `ManifestProvenance.EMPTY`, so every current caller in `ResourceUploadServiceTest`,
     `ResourceRepositoryLoaderServiceTest` and `ImportManifestTest` compiles unchanged.
2. **Manifest parsing (`.../domain/load/service/ResourceZipService.java`, `validateManifest`).**
   - Add `runId`, `generator`, `generatorVersion`, `contentSha256` and `matchCounts` to `allowedFields`. Change the
     shared shape message to "manifest.json must contain source, seasons, assets and optionally mode, runId,
     generator, generatorVersion, contentSha256, matchCounts".
   - Parse each field only when present; a present value must be valid (JSON `null` is rejected):
     `runId` text matching `[A-Za-z0-9._-]{1,64}`; `generator` and `generatorVersion` text, not blank, at most
     100 characters; `contentSha256` text matching `[0-9a-f]{64}`; `matchCounts` an object with exactly the keys
     `expected`, `withResult`, `pending`, each an integral JSON number that fits a `long`, then the
     `MatchCounts` invariants. Each failure is an `IllegalArgumentException` naming the field.
   - Return the `ImportManifest` with the parsed `ManifestProvenance`.
3. **Content hash check.**
   - New `ContentHash` in `.../domain/load/service/` (final class, private constructor):
     `static String compute(byte[] zipContent)` reads the ZIP with `ZipInputStream` and implements the algorithm.
     A duplicate entry name is an `IllegalArgumentException` (it would make the hash ambiguous); I/O errors become
     `IllegalArgumentException("Invalid ZIP file", e)`, as in `extractZip`.
   - `extractZipAndGetManifest(byte[])`: after `validateManifest`, when `provenance().contentSha256()` is present,
     compare it with `ContentHash.compute(content)` and throw
     `IllegalArgumentException("manifest.json contentSha256 does not match the ZIP content")` when it differs.
     `ImportResourceController.uploadZipFile` already maps `IllegalArgumentException` to 400. Temp-folder
     cleanup on failure stays as it is today (a failed validation already leaves the folder); it is out of scope.
4. **Java tests (JUnit 5, `tt-data-league-core-domain/src/test/...`).**
   - `ManifestProvenanceTest` / `MatchCountsTest`: invariants, `EMPTY`.
   - `ImportManifestTest`: the 4- and 5-argument constructors default to `ManifestProvenance.EMPTY`; a null
     provenance is rejected.
   - `ResourceZipServiceTest`: every new field absent (old manifests unchanged) and every field present and parsed;
     each malformed value rejected (bad `runId` characters or length, blank `generator`, 101-character
     `generatorVersion`, upper-case or 63-character `contentSha256`, `matchCounts` with a missing, extra, negative,
     fractional or inconsistent value, explicit `null`). Keep `rejectsAnUnknownExtraKey`.
   - `ContentHashTest`: the same entries written in two different orders and compression levels give the same hash;
     `manifest.json` and directory entries do not change the hash; a nested `x/manifest.json` does; a duplicate
     entry is rejected; **shared fixture**: a three-entry ZIP built in the test (`actas-json/2026-2027/a.json`,
     `actas-json/2026-2027/jornada-1/b.json`, `equipos-json/2026-2027.json` with fixed short contents) must hash to
     a literal constant that is also asserted by the Python test in step 9.
   - `extractZipAndGetManifest` through a built ZIP: matching hash accepted, mismatching hash rejected.
5. **Python hash and manifest (`tt-league-ingest/packages/tt-league-ingest-common/src/ingest_common/packaging.py`).**
   - `content_sha256(entries: list[tuple[Path, str]]) -> str` implements the algorithm from the `(file, arcname)`
     entries before the ZIP is written; duplicate arcnames raise `PackagingError`.
   - `RUN_ID_PATTERN = re.compile(r"[A-Za-z0-9._-]{1,64}")`, `GENERATOR = "tt-league-ingest"`.
   - `build_manifest(source, seasons, assets, mode=None, provenance: dict[str, Any] | None = None)` adds only the
     keys present in `provenance`, after the existing keys. Unknown provenance keys raise `PackagingError`.
   - `match_counts(paths: list[Path]) -> dict[str, int]` reads each packaged acta: `acta_publicada` false means
     pending; true or missing means with result (the schema says a missing value means published). A file that is
     not a JSON object, or whose `acta_publicada` is not a boolean, raises `PackagingError` naming the file.
   - `package_season(..., run_id: str | None = None)`: rejects a `run_id` that does not match `RUN_ID_PATTERN`;
     always sets `generator`, `generatorVersion = importlib.metadata.version("tt-league-ingest-common")` and
     `contentSha256`; sets `matchCounts` over the packaged ACTAS entries when the package has actas (a TEAMS-only
     package has no `matchCounts`); sets `runId` when given. This applies to dry runs too, so `--dry-run --json`
     shows the manifest that would be written. `_write_zip` is unchanged and still writes entries sorted by name.
6. **Run id plumbing.**
   - `ingest_common/run.py`: `IngestRequest.run_id: str | None = None`.
   - `ingest_common/pipeline.py`: `_package` passes `run_id=request.run_id` to `package_season` and turns a
     `PackagingError` into a failed stage, as it does today.
   - `ingest_cli/main.py`: `--run-id` on `package` and `run` (in `_add_package`), passed to `IngestRequest`.
   - `ingest_rest/app.py`: `create_run` builds the run id first (`uuid.uuid4().hex`), then the request with
     `run_id=<id>` and `zip_path=settings.packages_dir(source) / "runs" / f"{run_id}.zip"` (`dataclasses.replace`
     on the request returned by `to_request`). `RunRegistry.add` takes the prepared id. Every REST run therefore
     packages to its own file, so a later run of the same source, season and mode never replaces an earlier run's
     ZIP. The CLI default path (`default_zip_path`) is unchanged.
7. **Package download (`ingest_rest/app.py`).**
   - `GET /api/v1/ingest/runs/{run_id}/package` behind `require_key`: 404 for an unknown run; 409 while the run is
     `QUEUED` or `RUNNING`; 404 when the report has no `outputs["package"]` (no `PACKAGE` stage, the stage skipped
     with `no JSON changed`, failed, or pipeline error) or the file is no longer on disk; otherwise
     `FileResponse(path, media_type="application/zip", filename=path.name)` with header `X-Content-SHA256`
     = lowercase hex SHA-256 of the file bytes (read in chunks).
   - Retention: when a run finishes, delete `runs/*.zip` files of runs that are no longer among the
     `HISTORY_LIMIT` most recent registry records. The endpoint then answers 404 for those runs. The orchestrator
     keeps its own copy (FEAT-00096 D5), so ingest only needs to keep the ZIP until it is fetched.
8. **Python tests.**
   - `tt-league-ingest-common/tests/test_packaging_pipeline.py`: manifest has `generator`, `generatorVersion`,
     `contentSha256`, `matchCounts` (published, unpublished and missing `acta_publicada`), `runId` only when given;
     two builds of the same content give the same `contentSha256`, and recomputing it from the written ZIP's entries
     matches; a TEAMS-only package has no `matchCounts`; invalid `run_id` and an acta with a non-boolean
     `acta_publicada` raise `PackagingError`; the **shared fixture** constant from step 4.
   - `tt-league-ingest-cli/tests/test_cli.py`: `--run-id` reaches the request and the manifest.
   - `tt-league-ingest-rest/tests/test_rest.py`: the run id is in the packaged manifest; two runs package to two
     files; package endpoint 200 (body, `X-Content-SHA256`, content type), 404 unknown run, 404 no package
     (skipped `PACKAGE`), 409 while running, 401 without key; pruning beyond `HISTORY_LIMIT` gives 404.
9. **Docs.**
   - `tt-data-league-api-runtime/README.md`, "ZIP import upload contract": the five optional keys with their
     validation rules, the hash algorithm, the 400 on mismatch, and an example manifest with provenance. Old
     manifests without them stay valid.
   - `tt-league-ingest/README.md`: the manifest fields written by the packager, `--run-id`, the per-run package path
     of the REST service, `GET /api/v1/ingest/runs/{runId}/package` with its status codes and header, and retention.
   - The JPA schema contract (`rfetm-datamodel.md`) is unaffected: nothing is persisted.
10. **Validation.** `mvn -pl tt-data-league-core-domain -am test`, then the full `mvn test` from the root, then in
    `tt-league-ingest/`: `uv lock --check`, `uv sync --all-packages`, `uv run pytest`.

## Acceptance Criteria

- [x] `manifest.json` gains optional `runId`, `generator`, `generatorVersion`, `contentSha256` and `matchCounts` (`expected`, `withResult`, `pending`) fields
- [x] `contentSha256` follows one documented algorithm over the sorted ZIP entries other than `manifest.json`, so identical content always gives the same hash
- [x] `ResourceZipService` accepts the new optional fields, validates their format, recomputes `contentSha256` from the ZIP content and rejects a mismatch with 400, and still accepts manifests without them
- [x] The Java change ships before or with the Python change, because the platform rejects unknown manifest keys today
- [x] `GET /api/v1/ingest/runs/{runId}/package` streams the run's ZIP with its SHA-256 in an `X-Content-SHA256` header (404 when the run is unknown or produced no ZIP, 409 while the run is active)
- [x] Each REST run packages to its own ZIP, so a later run never replaces an earlier run's package; packages of runs outside the most recent `HISTORY_LIMIT` are deleted and answer 404
- [x] The manifest section of `tt-data-league-api-runtime/README.md` and `tt-league-ingest/README.md` describe the new fields and the hash algorithm
- [x] Python and Java tests cover hash stability, optional-field parsing, mismatch rejection and rejection of malformed values

# Implementation Guidelines

- Crosses the Python/Java boundary: `ResourceZipService` owns the contract, so change it in the same change as the
  Python packager. Deploy the platform first; an older platform rejects the new keys with 400.
- Old ZIPs without the new fields must keep importing unchanged.
- The ZIP-level idempotency that uses `contentSha256` belongs to FEAT-00100, not here.
- `ImportManifest` only carries the provenance; no import, persistence or API response uses it yet (FEAT-00100 will).
  Nothing is persisted, so `rfetm-datamodel.md` does not change.
- `runId` is the ingest run id (the REST service's `runId`, or `--run-id` on the CLI). Correlating it with the
  orchestrator's own run id is FEAT-00115.
- Out of scope: a persistent ingest run registry (runs and their packages are still forgotten on restart; the
  package endpoint then answers 404), signing the manifest, and cleaning up the platform's temp extraction folder.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 4 (manifest inside every ZIP). The ZIP-level idempotency that uses `contentSha256` is in the import jobs API item.

## Planning notes (2026-10-04)

- `ResourceZipService.validateManifest` rejects every key outside `source`, `seasons`, `assets` and `mode`
  (`rejectsAnUnknownExtraKey` test). This is why the Java change must ship first.
- Verifying `contentSha256` on the platform makes it safe to use as an idempotency key in FEAT-00100: a client cannot
  claim a hash for content it did not send.
- `X-Content-SHA256` (the ZIP file's own hash) lets the orchestrator check a transfer; `contentSha256` (the manifest's
  content hash) identifies the content regardless of ZIP packing.

## Plan rebuild (2026-10-04, after FEAT-00098)

The plan was checked against the code after FEAT-00097 and FEAT-00098 merged. Changes and why:

- **Java hashes the ZIP bytes, not the extracted folder.** The previous plan used `ContentHash.compute(Path)` over
  the extraction folder. Walking the file system ties the result to path separators, Unicode normalisation and
  case handling on the host. Hashing the entries as stored in the ZIP is exactly what Python hashes. For the same
  reason the sort order is now defined as UTF-8 byte order: Java `String.compareTo` sorts by UTF-16 units and
  would differ from Python for characters outside the BMP. The mismatch check therefore moves from
  `validateManifest(Path)` to `extractZipAndGetManifest(byte[])`, the only production caller
  (`ResourceUploadService`).
- **Per-run package files in the REST service.** `IngestPipeline._package` writes to
  `default_zip_path(request)` (`packages/actas-json-<season>[-delta].zip`) unless `zip_path` is set, and the REST
  service never sets it. A later run of the same source, season and mode would overwrite the ZIP that an earlier
  run's `/package` endpoint is supposed to return, and with it the `X-Content-SHA256` value. REST runs now package
  to `packages/runs/<runId>.zip`, and the newest `HISTORY_LIMIT` runs keep their files. A new acceptance criterion
  records this.
- **Run id is generated before the request.** `RunRegistry.add` currently creates the id after the frozen
  `IngestRequest` exists, so the request could not carry it. The id is now generated first.
- **`package_season` gained `select` and scoped delta packaging in FEAT-00098.** `matchCounts` therefore counts the
  actas actually packaged (after scope and `jornada` selection), not the whole season.
- **Packaging fails clearly on an unreadable acta** while computing `matchCounts`, instead of counting it silently.
- The 5-argument `ImportManifest` constructor is the current canonical one. It becomes a delegating constructor
  so existing callers compile unchanged.
- The status stays `ready`: file ownership, contracts, ordering and tests are all specified.

## Implementation notes (2026-10-04)

- Java: `ManifestProvenance` and `MatchCounts` in `resource/model`; `ImportManifest` gained the `provenance`
  component (4- and 5-argument constructors default to `ManifestProvenance.EMPTY`). `ResourceZipService` parses
  and validates the five keys in `validateManifest`, and `extractZipAndGetManifest` compares a declared
  `contentSha256` with `ContentHash.compute(byte[])`. `ContentHash` rejects duplicate entry names.
- Python: `packaging.content_sha256`, `packaging.match_counts`, `build_manifest(..., provenance=)`,
  `package_season(..., run_id=)`. `RUN_ID_PATTERN` lives in `packaging.py` and is reused by
  `IngestRequest.__post_init__`, so an invalid `--run-id` is a CLI usage error and an invalid run id can never
  reach the packager. Hashing reuses `fingerprint.file_digest`.
- REST: the run id is generated before the request; only runs that include `package` get the per-run
  `packages/runs/<runId>.zip` path, so an `upload`-only REST run still uses the default ZIP path as before.
  Pruning runs after every run ends, deletes ZIPs of runs outside the `HISTORY_LIMIT` most recent ones, and logs
  (does not fail the run) when a file cannot be deleted.
- Shared fixture: `ContentHashTest.SHARED_FIXTURE_HASH` and `test_packaging_pipeline.SHARED_FIXTURE_HASH` are the
  same literal (`9784c2c5…77c8`). The fixture includes two names whose UTF-16 and UTF-8 orders differ, so it also
  pins the sort order.
- Validation: `mvn -pl tt-data-league-core-domain -am test` green (310 tests); `uv lock --check`,
  `uv sync --all-packages`, `uv run pytest` green (295 passed). Full `mvn test`: every module passes except
  `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas`, which fails independently of this
  change because its fixture `actas/acta_bcnesa_2026_published.json` was never committed (FEAT-00094, `aad7415`);
  unrelated to this feature.
