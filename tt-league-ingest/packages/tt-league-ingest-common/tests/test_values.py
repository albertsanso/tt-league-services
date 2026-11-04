from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import pytest

from ingest_common.http import HttpError, PoliteHttpClient, RequestPacer, decode_html
from ingest_common.json_io import WriteOutcome, write_if_changed, write_json_atomically
from ingest_common.match_days import parse_match_days
from ingest_common.season import Season
from ingest_common.settings import ConfigurationError, IngestSettings
from ingest_common.source import Source
from ingest_common.text import clean_text, fold, slugify
from ingest_common.validation import ACTA_SCHEMA_PATH, TEAM_SCHEMA_PATH, ActaValidator, TeamsValidator

REPO_DOCS = Path(__file__).resolve().parents[4] / "docs"


def test_season_parsing_and_forms():
    season = Season.parse("2026-2027")
    assert str(season) == "2026-2027" and season.slash_form() == "2026/2027"
    for bad in ("2026-2028", "2026", "26-27", "2027-2026"):
        with pytest.raises(ValueError):
            Season.parse(bad)


def test_current_season_boundaries():
    assert str(Season.current(date(2026, 7, 31))) == "2025-2026"
    assert str(Season.current(date(2026, 8, 1))) == "2026-2027"
    assert str(Season.current(date(2027, 1, 15))) == "2026-2027"


def test_source_parsing_is_case_insensitive_and_strict():
    assert Source.parse("fctt") is Source.FCTT
    assert Source.parse(" Bcnesa ") is Source.BCNESA
    with pytest.raises(ValueError):
        Source.parse("fedesp")


@pytest.mark.parametrize("text,expected", [("3", {3}), ("1,4", {1, 4}), ("2-5", {2, 3, 4, 5}), ("1, 3-4", {1, 3, 4})])
def test_match_days(text, expected):
    assert parse_match_days(text) == frozenset(expected)


@pytest.mark.parametrize("text", ["", "0", "-1", "5-2", "a", "1,,x"])
def test_match_days_rejects_invalid(text):
    with pytest.raises(ValueError):
        parse_match_days(text)


def test_text_helpers():
    assert clean_text("  a\xa0 b\n c ") == "a b c"
    assert fold("Ñandú Épico") == "nandu epico"
    assert slugify("RTB VETERANS 1ª") == "rtb-veterans-1a"


def test_settings_require_existing_data_dir(tmp_path):
    with pytest.raises(ConfigurationError, match="TT_INGEST_DATA_DIR"):
        IngestSettings.build(None, env={})
    with pytest.raises(ConfigurationError, match="not an existing directory"):
        IngestSettings.build(None, env={"TT_INGEST_DATA_DIR": str(tmp_path / "missing")})
    settings = IngestSettings.build(None, env={"TT_INGEST_DATA_DIR": str(tmp_path)})
    assert settings.actas_json_dir(Source.FCTT) == tmp_path / "fctt" / "actas-json"
    with pytest.raises(ConfigurationError, match="TT_LEAGUE_API_URL and TT_LEAGUE_API_TOKEN"):
        settings.require_upload()


def test_atomic_and_unchanged_writes(tmp_path):
    target = tmp_path / "a" / "x.json"
    write_json_atomically(target, {"b": "é"})
    assert target.read_text(encoding="utf-8") == '{\n  "b": "é"\n}\n'
    assert write_if_changed(target, {"b": "é"}) is WriteOutcome.UNCHANGED
    assert write_if_changed(target, {"b": "é"}, force=True) is WriteOutcome.WRITTEN
    assert write_if_changed(target, {"b": "x"}) is WriteOutcome.WRITTEN
    assert not list(target.parent.glob(".*.tmp"))


def test_decode_html_falls_back_to_latin1():
    assert decode_html("camión".encode("utf-8")) == "camión"
    assert decode_html("camión".encode("iso-8859-1")) == "camión"


class FakeClock:
    def __init__(self):
        self.now = 0.0
        self.slept = []

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.slept.append(seconds)
        self.now += seconds


def test_pacer_enforces_minimum_delay():
    clock = FakeClock()
    pacer = RequestPacer(2.0, clock.monotonic, clock.sleep)
    pacer.wait()
    clock.now += 0.5
    pacer.wait()
    assert clock.slept == [1.5]


class FakeResponse:
    def __init__(self, status, body=b"", headers=None):
        self.status_code, self.content, self.headers = status, body, headers or {}


class FakeSession:
    def __init__(self, responses):
        self.responses, self.headers, self.calls = list(responses), {}, 0

    def get(self, url, timeout):
        self.calls += 1
        return self.responses.pop(0)

    def close(self):
        pass


def make_client(responses, **options):
    clock = FakeClock()
    session = FakeSession(responses)
    client = PoliteHttpClient(delay_seconds=0, backoff_seconds=2, session=session,
                              pacer=RequestPacer(0, clock.monotonic, clock.sleep), sleep=clock.sleep,
                              jitter=lambda: 0, **options)
    return client, session, clock


def test_http_retries_listed_statuses_with_backoff_and_retry_after():
    client, session, clock = make_client(
        [FakeResponse(404), FakeResponse(500, headers={"Retry-After": "7"}), FakeResponse(200, b"ok")],
        retry_status={404, 500})
    assert client.get("http://x/").body == b"ok"
    assert session.calls == 3 and clock.slept == [2, 7.0]


def test_http_accepts_listed_status_without_retry_and_fails_fast_otherwise():
    client, session, _ = make_client([FakeResponse(500, b"<html>")], accept_status={500})
    assert client.get("http://x/").status == 500 and session.calls == 1
    client, session, _ = make_client([FakeResponse(403)], retries=3)
    with pytest.raises(HttpError):
        client.get("http://x/")
    assert session.calls == 1


def test_http_gives_up_after_retry_budget():
    client, session, _ = make_client([FakeResponse(404)] * 3, retries=2, retry_status={404})
    with pytest.raises(HttpError, match="3 attempt"):
        client.get("http://x/")
    assert session.calls == 3


def acta(**overrides):
    base = json.loads((Path(__file__).parent / "fixtures" / "acta-published.json").read_text(encoding="utf-8"))
    base.update(overrides)
    return base


def test_validator_accepts_published_unpublished_and_rejects_broken():
    validator = ActaValidator()
    published = acta()
    assert validator.errors(published) == []
    unpublished = json.loads((Path(__file__).parent / "fixtures" / "acta-unpublished.json").read_text(encoding="utf-8"))
    assert validator.errors(unpublished) == []
    broken = dict(published)
    del broken["jornada"]
    assert validator.errors(broken)
    assert TeamsValidator().errors([]) is not None


def test_packaged_schemas_match_repository_docs():
    """Schema drift test: the packaged copies must stay byte-identical to docs/*.json."""
    assert ACTA_SCHEMA_PATH.read_bytes() == (REPO_DOCS / "acta-model-definition.json").read_bytes()
    assert TEAM_SCHEMA_PATH.read_bytes() == (REPO_DOCS / "team-model-definition.json").read_bytes()
