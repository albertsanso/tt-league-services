"""Entry point: ``tt-league-ingest-rest``."""

from __future__ import annotations

import os
import sys

import uvicorn

from ingest_common.settings import ConfigurationError, IngestSettings
from ingest_rest.app import create_app

API_KEY_VARIABLE = "TT_INGEST_REST_API_KEY"
HOST_VARIABLE = "TT_INGEST_REST_HOST"
PORT_VARIABLE = "TT_INGEST_REST_PORT"


def main() -> int:
    api_key = os.environ.get(API_KEY_VARIABLE, "").strip()
    if not api_key:
        print(f"error: {API_KEY_VARIABLE} is required", file=sys.stderr)
        return 2
    try:
        settings = IngestSettings.build()
        port = int(os.environ.get(PORT_VARIABLE, "8090"))
    except (ConfigurationError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    uvicorn.run(create_app(settings, api_key), host=os.environ.get(HOST_VARIABLE, "127.0.0.1"), port=port)
    return 0


if __name__ == "__main__":
    sys.exit(main())
