# tt-league-ingest

Python (uv) workspace that produces the `actas-json` / `equipos-json` exports and the upload ZIPs consumed by
the Java import (`tt-data-league-import*`) and by `POST /api/v1/administration/import/upload`. It ports the
**current-season incremental pipelines** of the former standalone extractors (`rfetm-extract-2`,
`bcnesa-extract-2`, `fctt-extract`); historical (archived) pipelines are out of scope. It is not part of the
Maven reactor: `mvn test` does not run these tests.

## Prerequisites

- [uv](https://docs.astral.sh/uv/) and Python 3.12+ (`uv sync` fetches the interpreter when needed).

```text
cd tt-league-ingest
uv sync --all-packages
```

## Workspace layout

| Package | Import name | Purpose |
| --- | --- | --- |
| `tt-league-ingest-common` | `ingest_common` | Season/source values, settings, run model, HTTP client, JSON IO, schema validation, packaging, upload client, stage pipeline |
| `tt-league-ingest-rfetm` | `ingest_rfetm` | RFETM actas (HTML + PDF) and teams |
| `tt-league-ingest-bcnesa` | `ingest_bcnesa` | BCNESA actas (HTML) |
| `tt-league-ingest-fctt` | `ingest_fctt` | FCTT actas (HTML) |
| `tt-league-ingest-cli` | `ingest_cli` | `tt-league-ingest` command |
| `tt-league-ingest-rest` | `ingest_rest` | `tt-league-ingest-rest` FastAPI service |

Federation packages register their `SourceIngestor` under the entry-point group `tt_league_ingest.sources`.

## Configuration

| Variable | Required | Purpose |
| --- | --- | --- |
| `TT_INGEST_DATA_DIR` | yes (or `--data-dir`) | Existing directory; each source uses `<data_dir>/<source>/{content,actas-json,equipos-json,packages,logs}` |
| `TT_LEAGUE_API_URL` | upload stage | Base URL of `tt-data-league-api-runtime` |
| `TT_LEAGUE_API_TOKEN` | upload stage | Bearer token for the import endpoint (never logged) |
| `TT_INGEST_REST_API_KEY` | REST service | Value expected in the `X-API-Key` header; startup fails without it |
| `TT_INGEST_REST_HOST` | no | Default `127.0.0.1` |
| `TT_INGEST_REST_PORT` | no | Default `8090` |

## Output contracts (unchanged from the legacy extractors)

- RFETM: `actas-json/<season>/<category>/<day>/<sex>/acta_<localId>_<awayId>.json`; teams `equipos-json/<season>.json`.
- BCNESA: `actas-json/<season>/<rtb-competition>/<G<n>|Other>/<phase>/jornada_<NN>_local_team_<id>_away_team_<id>.json`.
- FCTT: `actas-json/<season>/<category>/<group>/<phase>/jornada_<N>_local_team_<id>_away_team_<id>.json`.

The packaged JSON schemas (`ingest_common/schema/`) are copies of `docs/acta-model-definition.json` and
`docs/team-model-definition.json`; a drift test keeps them identical.

## Match-day status file

Every `download` stage ends by rebuilding `<data_dir>/<source>/content/match-days-status.json` from the
pages saved on disk, even when the download had failures. The file therefore always describes the downloaded
content, and the update-matches management view can use it as the global status. Each match day (one saved
jornada page of one group) gets one status:

| Status | Meaning |
| --- | --- |
| `complete` | every match has its acta published |
| `partial` | some matches are played, but not every acta is published yet |
| `scheduled` | nothing played yet, but a match date has passed (or is unknown): results pending |
| `future` | nothing played yet and every match is still to come |

The file holds `source`, `generatedAt`, `evaluatedAt` (local time used for the date comparison), `seasons`,
a `summary` (counts per status, `matches`, `played`, `reported`, `emptyPages`, `unreadablePages`) and a
`matchDays` list. Each entry has `season`, `category`, `group`, `phase`, `gender`, `territory`, `matchDay`,
`status`, `matches`, `played`, `reported`, `firstMatchAt`, `lastMatchAt`, `file` (relative to the content
folder) and `contentUpdatedAt`. Pages that list no matches are only counted in `emptyPages`. The file is
written atomically, so a reader never sees a half-written file.

## CLI

```text
uv run tt-league-ingest download --source rfetm --season 2026-2027 --category divisio-honor --match-day 3
uv run tt-league-ingest download --source bcnesa --territory Barcelona --match-day 3
uv run tt-league-ingest parse    --source bcnesa --season 2026-2027 --match-day 3 --force
uv run tt-league-ingest teams    --source rfetm --season 2026-2027
uv run tt-league-ingest package  --source fctt --season 2026-2027 --mode delta --match-day 3 [--dry-run] [--force] [--output FILE]
uv run tt-league-ingest upload   --source fctt --zip FILE [--allow-published-shrink]
uv run tt-league-ingest run      --source fctt --match-day 3 --package --upload --mode delta
```

`--season` defaults to the current season (a season starts in August). `--territory` restricts the
download (BCNESA: `Barcelona`, `Girona`, `Lleida`, `Tarragona`; FCTT: territory slug); parsing handles
whatever was downloaded. Filters a source does not support (`--group`/`--phase`/`--territory` for RFETM,
`--gender` outside FCTT, ...) fail the run before any network call. `--json`
prints the run report as JSON. Exit codes: `0` succeeded, `1` completed with issues or a failed stage,
`2` usage/configuration error.

Downloads are incremental. A match day already saved as `complete` is not requested again. A match day
saved as `partial` is refreshed on every run. A **future** match day (none of its matches has started)
that already has a saved page is not requested again, even if the calendar changed. The same rules
apply to RFETM: a saved jornada is refreshed only while it is in progress (a match has started but has
no published acta); complete jornadas (every match with an acta), future jornadas and pages without
matches are skipped. `--force` re-downloads everything selected. RFETM parsing regenerates a JSON when
its jornada HTML or acta PDF is newer than the JSON.

Current-jornada delta flow: `run --match-day N --package --mode delta --upload`. A delta ZIP only contains actas whose payload `jornada`
is one of the requested days and fails when nothing matches. A 409 from the upload (published-acta shrink check)
fails the stage; `--allow-published-shrink` must be given explicitly to override it.

## REST service

```text
TT_INGEST_REST_API_KEY=... TT_INGEST_DATA_DIR=... uv run tt-league-ingest-rest

curl -X POST localhost:8090/api/v1/ingest/runs -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"source":"fctt","season":"2026-2027","stages":["download","parse","package"],"filters":{"matchDays":"3"},"mode":"delta"}'
curl -H "X-API-Key: $KEY" localhost:8090/api/v1/ingest/runs/<runId>
```

Endpoints: `POST /api/v1/ingest/runs` (`202 {runId}`, `400` invalid input, `409` same source already running),
`GET /api/v1/ingest/runs/{runId}` (`404` unknown), `GET /api/v1/ingest/runs` (most recent first, default 50),
`GET /health` (no key). Runs execute one at a time; run history is in memory and lost on restart.

## Tests

```text
uv lock --check
uv run pytest                                   # whole workspace
uv run pytest packages/tt-league-ingest-fctt    # one package
```

Tests never use the network.
