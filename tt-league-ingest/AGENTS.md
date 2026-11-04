# tt-league-ingest instructions

Supplements the root `AGENTS.md`. This is a uv workspace outside the Maven reactor.

- **Dependency direction:** `ingest_cli` / `ingest_rest` -> `ingest_rfetm` / `ingest_bcnesa` / `ingest_fctt` ->
  `ingest_common`. Federation packages never import each other, `ingest_common` imports no federation package
  (sources are discovered through the `tt_league_ingest.sources` entry point), and nothing imports CLI/REST.
  `tests/test_workspace_layout.py` enforces this.
- **Port, do not rewrite:** the federation parsers are ported from the legacy extractors. Their output (field
  values, `acta_publicada`, file and folder names, JSON formatting) must stay byte-compatible so the Java
  navigators and the upload endpoint need no change. An output difference against the legacy extractors is a bug.
- **Schemas:** `ingest_common/schema/*.json` are copies of `docs/*.json`; never edit them independently, the drift
  test must stay green.
- **No network in tests;** use saved fixtures under `packages/*/tests/fixtures/` and fake transports.
- **Scraping etiquette:** keep the legacy delays, retries and User-Agent; the REST service runs one ingestion at a time.
- **Configuration is explicit:** no default data directory, season rule only as documented, no fallback to another
  source; missing required variables fail clearly. Never commit `.venv`, downloaded content, generated JSON, ZIPs,
  logs or tokens.
- Do not change Java code or the manifest contract from here (`ResourceZipService` owns it).
- Validation: `uv lock --check`, `uv sync --all-packages`, `uv run pytest` from `tt-league-ingest/`.
- The REST service configures logging only through `ingest_common.logs`. A legacy `configure_logging` must
  not touch the root logger or add console handlers while `service_logging_active()`; it only attaches its
  own file handler and lets records propagate to the root JSON handler.
