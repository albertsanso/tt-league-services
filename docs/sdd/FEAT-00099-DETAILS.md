# Build Plan
**Hash algorithm (shared contract).** For every ZIP entry except `manifest.json` and directory entries, sorted
by entry name (UTF-8, `/` separators): append `<name>\n<lowercase hex SHA-256 of the entry bytes>\n` to one buffer;
`contentSha256` is the lowercase hex SHA-256 of that buffer. Independent of ZIP timestamps and compression.

1. **Domain model (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/resource/model/`).**
   - New records `ManifestProvenance(Optional<String> runId, Optional<String> generator, Optional<String> generatorVersion,
     Optional<String> contentSha256, Optional<MatchCounts> matchCounts)` with `EMPTY`, and
     `MatchCounts(long expected, long withResult, long pending)` validating non-negative values and
     `withResult + pending == expected`.
   - `ImportManifest`: add component `ManifestProvenance provenance` (non-null, defaults to `EMPTY`); keep the existing
     4- and 5-argument constructors delegating with `ManifestProvenance.EMPTY`, so current callers compile unchanged.
2. **Validation (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/load/service/ResourceZipService.java`).**
   - Extend `allowedFields` with the five keys; update the error message to list them.
   - Parse: `runId` 1-64 chars `[A-Za-z0-9._-]`; `generator` and `generatorVersion` non-blank text up to 100 chars;
     `contentSha256` exactly 64 lowercase hex chars; `matchCounts` an object with exactly the three integer keys.
   - New static `ContentHash.compute(Path extractionFolder)` (same package) implementing the algorithm over the
     extracted tree; when `contentSha256` is present and differs, throw `IllegalArgumentException`
     ("manifest.json contentSha256 does not match the ZIP content"), which the upload controller already maps to 400.
3. **Java tests (`ResourceZipServiceTest`, new `ContentHashTest`).** Optional fields absent/present; each malformed
   field rejected; mismatch rejected; hash independent of file creation order; a fixture hash value shared with the
   Python test to prove both implementations agree.
4. **Python packager (`tt-league-ingest/packages/tt-league-ingest-common/src/ingest_common/packaging.py`).**
   - `build_manifest(..., provenance: dict | None = None)` merges the optional keys.
   - `content_sha256(entries)` implementing the algorithm from the `(path, name)` entries before writing the ZIP.
   - `package_season(..., run_id: str | None = None)` computes `matchCounts` from each packaged acta's `acta_publicada`
     (missing means published, as in the schema), sets `generator = "tt-league-ingest"` and
     `generatorVersion = importlib.metadata.version("tt-league-ingest-common")`, and includes `runId` when given.
5. **Run id plumbing.** `IngestRequest.run_id: str | None`; the REST service passes its `run_id`; the CLI accepts
   `--run-id`. `IngestPipeline._package` forwards it.
6. **Package download (`tt-league-ingest/packages/tt-league-ingest-rest/src/ingest_rest/app.py`).**
   `GET /api/v1/ingest/runs/{run_id}/package` (`X-API-Key`): 404 unknown run or no `outputs["package"]`, 409 while
   `QUEUED`/`RUNNING`; otherwise `FileResponse(media_type="application/zip")` with `X-Content-SHA256` (SHA-256 of the
   ZIP file bytes) and `Content-Disposition`.
7. **Python tests.** `test_packaging_pipeline.py`: manifest fields, deterministic `contentSha256` across two builds,
   the shared fixture hash, match counts; `test_rest.py`: package endpoint 200/404/409 and header.
8. **Docs.** `tt-data-league-api-runtime/README.md` "ZIP import upload contract": the new optional keys, validation
   rules and hash algorithm; `tt-league-ingest/README.md`: manifest fields, `--run-id`, package endpoint.
9. **Validation.** `mvn -pl tt-data-league-core-domain -am test`, the full `mvn test`, and `uv run pytest` in
   `tt-league-ingest/`.

## Acceptance Criteria

- [ ] `manifest.json` gains optional `runId`, `generator`, `generatorVersion`, `contentSha256` and `matchCounts` (`expected`, `withResult`, `pending`) fields
- [ ] `contentSha256` follows one documented algorithm over the sorted ZIP entries other than `manifest.json`, so identical content always gives the same hash
- [ ] `ResourceZipService` accepts the new optional fields, validates their format, recomputes `contentSha256` from the extracted files and rejects a mismatch with 400, and still accepts manifests without them
- [ ] The Java change ships before or with the Python change, because the platform rejects unknown manifest keys today
- [ ] `GET /api/v1/ingest/runs/{runId}/package` streams the run's ZIP with its SHA-256 in an `X-Content-SHA256` header (404 when the run is unknown or produced no ZIP, 409 while the run is active)
- [ ] The manifest section of `tt-data-league-api-runtime/README.md` and `tt-league-ingest/README.md` describe the new fields and the hash algorithm
- [ ] Python and Java tests cover hash stability, optional-field parsing, mismatch rejection and rejection of malformed values

# Implementation Guidelines

- Crosses the Python/Java boundary: `ResourceZipService` owns the contract, so change it in the same change as the
  Python packager. Deploy the platform first; an older platform rejects the new keys with 400.
- Old ZIPs without the new fields must keep importing unchanged.
- The ZIP-level idempotency that uses `contentSha256` belongs to FEAT-00100, not here.

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
