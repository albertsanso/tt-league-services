"""Regenerate the expected golden outputs: ``uv run python tests/golden/regenerate.py [scenario...]``.

The stored expected outputs were produced by the original ports (``ingest_bcnesa`` / ``ingest_fctt``), which the
unified package reproduces byte for byte. Regenerate only for an intentional, reviewed output change (a dedicated
commit whose diff shows exactly that change).
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from golden_adapters import FcttWebAdapter  # noqa: E402
from golden_harness import run_scenario, write_expected  # noqa: E402
from golden_scenarios import SCENARIOS  # noqa: E402


def main(argv: list[str]) -> int:
    adapter = FcttWebAdapter()
    wanted = set(argv)
    for scenario in SCENARIOS:
        if wanted and scenario.key not in wanted:
            continue
        snapshots = run_scenario(adapter, scenario)
        write_expected(scenario, snapshots)
        print(f"{scenario.key}: {len(snapshots)} round(s), {sum(len(s.files) for s in snapshots)} file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
