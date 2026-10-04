# Build Plan

> Draft outline.

1. `ingest_common/packaging.py`: compute `contentSha256` and `matchCounts` (from `acta_publicada`); add `runId`/generator fields.
2. `ingest_rest/app.py`: package download endpoint; keep the package path per run.
3. `tt-data-league-core-domain` `ImportManifest` / `ResourceZipService`: parse the optional fields; tests in `ResourceZipServiceTest`.
4. Update the manifest documentation in `tt-data-league-api-runtime/README.md` and `tt-league-ingest/README.md`.

## Acceptance Criteria

- [ ] `manifest.json` gains optional `runId`, `generator`, `generatorVersion`, `contentSha256` and `matchCounts` (`expected`, `withResult`, `pending`) fields
- [ ] `contentSha256` is computed deterministically over the sorted ZIP entries (excluding the manifest), so identical content gives the same hash
- [ ] `ResourceZipService` accepts the new optional fields, validates their types, and still accepts manifests without them
- [ ] `GET /api/v1/ingest/runs/{runId}/package` streams the run's ZIP with its SHA-256 in a response header (404 when the run produced none)
- [ ] The upload manifest section of `tt-data-league-api-runtime/README.md` and `tt-league-ingest/README.md` describe the new fields
- [ ] Python and Java tests cover hash stability, optional-field parsing and rejection of malformed values

# Implementation Guidelines

- Crosses the Python/Java boundary: change `ResourceZipService` (owner of the contract) and the Python packager in the same change.
- Old ZIPs without the new fields must keep importing unchanged.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 4 (manifest inside every ZIP). The ZIP-level idempotency that uses `contentSha256` is in the import jobs API item.
