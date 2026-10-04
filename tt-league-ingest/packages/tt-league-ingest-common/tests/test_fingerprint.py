from __future__ import annotations

import os

from ingest_common.fingerprint import content_fingerprint, count_changes, file_digest, json_fingerprint


def test_count_changes_counts_added_modified_and_removed():
    before = {"a": "1", "b": "2", "c": "3"}
    after = {"a": "1", "b": "changed", "d": "4"}
    assert count_changes(before, after) == 3  # b modified, d added, c removed
    assert count_changes(before, before) == 0
    assert count_changes({}, {}) == 0
    assert count_changes({}, {"x": 1}) == 1
    assert count_changes({"x": 1}, {}) == 1


def test_json_fingerprint_hashes_bytes_and_ignores_other_files(tmp_path):
    (tmp_path / "g1").mkdir()
    (tmp_path / "g1" / "acta.json").write_text('{"a": 1}', encoding="utf-8")
    (tmp_path / "g1" / "page.html").write_text("<html/>", encoding="utf-8")
    first = json_fingerprint(tmp_path)
    assert list(first) == ["g1/acta.json"]
    assert first["g1/acta.json"] == file_digest(tmp_path / "g1" / "acta.json")
    assert json_fingerprint(tmp_path) == first
    (tmp_path / "g1" / "acta.json").write_text('{"a": 2}', encoding="utf-8")
    assert count_changes(first, json_fingerprint(tmp_path)) == 1


def test_json_fingerprint_of_missing_directory_is_empty(tmp_path):
    assert json_fingerprint(tmp_path / "missing") == {}


def test_content_fingerprint_lists_only_requested_patterns(tmp_path):
    (tmp_path / "sub").mkdir()
    page = tmp_path / "sub" / "jornada_01.html"
    page.write_text("<html/>", encoding="utf-8")
    (tmp_path / "acta.pdf").write_bytes(b"%PDF")
    (tmp_path / "notes.txt").write_text("ignored", encoding="utf-8")
    found = content_fingerprint(tmp_path, ("*.html", "*.pdf"))
    assert set(found) == {"sub/jornada_01.html", "acta.pdf"}
    assert found["acta.pdf"][0] == 4


def test_content_fingerprint_detects_size_and_mtime_changes(tmp_path):
    page = tmp_path / "page.html"
    page.write_text("one", encoding="utf-8")
    before = content_fingerprint(tmp_path, ("*.html",))
    assert count_changes(before, content_fingerprint(tmp_path, ("*.html",))) == 0
    stat = page.stat()
    os.utime(page, ns=(stat.st_atime_ns, stat.st_mtime_ns + 1_000_000_000))
    assert count_changes(before, content_fingerprint(tmp_path, ("*.html",))) == 1


def test_content_fingerprint_of_missing_directory_is_empty(tmp_path):
    assert content_fingerprint(tmp_path / "missing", ("*.html",)) == {}
