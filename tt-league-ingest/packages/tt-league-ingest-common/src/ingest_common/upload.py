"""Upload of a package ZIP to the platform import endpoint."""

from __future__ import annotations

from pathlib import Path

import requests

UPLOAD_PATH = "/api/v1/administration/import/upload"


class UploadError(Exception):
    """The platform rejected the upload or could not be reached."""


class PlatformUploader:
    """Posts the ZIP as multipart ``file`` with a bearer token. The token is never logged."""

    def __init__(self, api_url: str, token: str, *, timeout: float = 600.0,
                 session: requests.Session | None = None) -> None:
        self._url = api_url.rstrip("/") + UPLOAD_PATH
        self._token = token
        self._timeout = timeout
        self._session = session or requests.Session()

    def upload(self, zip_path: Path, *, allow_published_shrink: bool = False) -> str:
        """Return the server response body. 400/409 and other failures raise ``UploadError``."""
        if not zip_path.is_file():
            raise UploadError(f"ZIP not found: {zip_path}")
        data = {"allowPublishedShrink": "true"} if allow_published_shrink else {}
        try:
            with zip_path.open("rb") as stream:
                response = self._session.post(
                    self._url, files={"file": (zip_path.name, stream, "application/zip")}, data=data,
                    headers={"Authorization": f"Bearer {self._token}"}, timeout=self._timeout)
        except requests.RequestException as error:
            raise UploadError(f"upload to {self._url} failed: {type(error).__name__}: {error}") from error
        if response.status_code == 409:
            raise UploadError(f"upload rejected by the published-acta shrink check (409): {response.text.strip()}")
        if response.status_code == 400:
            raise UploadError(f"upload rejected as invalid (400): {response.text.strip()}")
        if not 200 <= response.status_code < 300:
            raise UploadError(f"upload failed with HTTP {response.status_code}: {response.text.strip()[:500]}")
        return response.text
