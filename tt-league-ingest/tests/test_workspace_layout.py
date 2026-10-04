"""Workspace layout and dependency-direction checks."""

from __future__ import annotations

import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = ROOT / "packages"
EXPECTED = {"common", "rfetm", "bcnesa", "fctt", "cli", "rest"}
FEDERATIONS = {"rfetm", "bcnesa", "fctt"}
PREFIX = "tt-league-ingest-"


def load(name: str) -> dict:
    return tomllib.loads((PACKAGES / f"{PREFIX}{name}" / "pyproject.toml").read_text(encoding="utf-8"))


def workspace_dependencies(name: str) -> set[str]:
    deps = set()
    for requirement in load(name)["project"]["dependencies"]:
        distribution = requirement.split(">")[0].split("=")[0].strip()
        if distribution.startswith(PREFIX):
            deps.add(distribution[len(PREFIX):])
    return deps


def test_expected_members_exist_with_package_modules():
    assert {p.name[len(PREFIX):] for p in PACKAGES.glob(f"{PREFIX}*")} == EXPECTED
    for name in EXPECTED:
        assert (PACKAGES / f"{PREFIX}{name}" / "src" / f"ingest_{name}" / "__init__.py").is_file()


def test_root_declares_workspace_members():
    root = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))
    assert root["tool"]["uv"]["workspace"]["members"] == ["packages/*"]
    for name in EXPECTED:
        assert root["tool"]["uv"]["sources"][f"{PREFIX}{name}"] == {"workspace": True}


def test_every_workspace_dependency_has_a_workspace_source():
    for name in EXPECTED:
        sources = load(name).get("tool", {}).get("uv", {}).get("sources", {})
        for dependency in workspace_dependencies(name):
            assert sources.get(f"{PREFIX}{dependency}") == {"workspace": True}, (name, dependency)


def test_dependency_direction():
    assert workspace_dependencies("common") == set()
    for federation in FEDERATIONS:
        assert workspace_dependencies(federation) == {"common"}
    for entry in ("cli", "rest"):
        assert workspace_dependencies(entry) == {"common"} | FEDERATIONS
    for name in EXPECTED:
        assert not workspace_dependencies(name) & {"cli", "rest"}


def test_source_imports_follow_the_direction():
    forbidden = {
        "common": {"ingest_rfetm", "ingest_bcnesa", "ingest_fctt", "ingest_cli", "ingest_rest"},
        "rfetm": {"ingest_bcnesa", "ingest_fctt", "ingest_cli", "ingest_rest"},
        "bcnesa": {"ingest_rfetm", "ingest_fctt", "ingest_cli", "ingest_rest"},
        "fctt": {"ingest_rfetm", "ingest_bcnesa", "ingest_cli", "ingest_rest"},
    }
    for name, modules in forbidden.items():
        for path in (PACKAGES / f"{PREFIX}{name}" / "src").rglob("*.py"):
            text = path.read_text(encoding="utf-8")
            for module in modules:
                assert f"import {module}" not in text and f"from {module}" not in text, (path, module)
