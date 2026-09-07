#!/usr/bin/env python3
"""Deterministic helpers for the SDD feature registry."""

from __future__ import annotations

import argparse
import os
import re
import sys
import tempfile
from pathlib import Path


STATUSES = ("idea", "planned", "ready", "in-progress", "in-review", "done", "blocked")
SECTIONS = ("In Progress", "In Review", "Backlog", "Done")
STATUS_SECTION = {
    "idea": "Backlog",
    "planned": "Backlog",
    "ready": "Backlog",
    "in-progress": "In Progress",
    "in-review": "In Review",
    "done": "Done",
    "blocked": "Backlog",
}
FEATURE_RE = re.compile(
    r"(?ms)^### \[(FEAT-\d{5})\] ([^\n]+)\n.*?(?=^### \[FEAT-\d{5}\] |^## |\Z)"
)
STATUS_RE = re.compile(r"^(- \*\*Status:\*\* )([^\n]+)$", re.MULTILINE)


class OperationError(Exception):
    """An expected validation or operation failure."""


def repository_root() -> Path:
    return Path(__file__).resolve().parents[4]


def read_utf8(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8").removeprefix("\ufeff")
    except FileNotFoundError as exc:
        raise OperationError(f"Missing file: {path}") from exc


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as stream:
            stream.write(content)
        os.replace(temporary_path, path)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def registry_path(root: Path) -> Path:
    return root / "docs" / "sdd" / "FEATURES.md"


def details_path(root: Path, feature_id: str) -> Path:
    return root / "docs" / "sdd" / f"{feature_id}-DETAILS.md"


def feature_id(value: str) -> str:
    normalized = value.upper()
    if not re.fullmatch(r"FEAT-\d{5}", normalized):
        raise OperationError(f"Invalid feature ID: {value}; expected FEAT-XXXXX.")
    return normalized


def feature_blocks(registry: str) -> dict[str, re.Match[str]]:
    return {match.group(1): match for match in FEATURE_RE.finditer(registry)}


def section_for_position(registry: str, position: int) -> str | None:
    section = None
    for match in re.finditer(r"^## (.+)$", registry, re.MULTILINE):
        if match.start() > position:
            break
        if match.group(1) in SECTIONS:
            section = match.group(1)
    return section


def section_body(registry: str, section: str) -> tuple[int, int, str]:
    header = re.search(rf"^## {re.escape(section)}\s*$", registry, re.MULTILINE)
    if not header:
        raise OperationError(f"Missing registry section: ## {section}")
    next_section = re.search(r"^## ", registry[header.end() :], re.MULTILINE)
    end = header.end() + next_section.start() if next_section else len(registry)
    return header.end(), end, registry[header.end() : end]


def rebuild_section(registry: str, section: str, blocks: list[str]) -> str:
    start, end, body = section_body(registry, section)
    if section == "Done":
        blocks.sort(key=lambda block: re.match(r"^### \[(FEAT-\d{5})\]", block).group(1), reverse=True)
    replacement = normalize_section_body_for(section, body, blocks)
    return registry[:start] + replacement + registry[end:]


def normalize_section_body_for(section: str, body: str, blocks: list[str]) -> str:
    body_without_features = FEATURE_RE.sub("", body)
    body_without_empty_marker = re.sub(
        r"(?m)^\s*No features currently (?:in progress|in review|in the backlog)\.\s*$",
        "",
        body_without_features,
    )
    prefix = body_without_empty_marker.rstrip()
    if prefix.endswith("---"):
        prefix = prefix[:-3].rstrip()
    if prefix:
        prefix += "\n\n"
    if blocks:
        prefix += "\n\n".join(block.rstrip() for block in blocks)
        prefix += "\n\n---"
    else:
        prefix += {
            "In Progress": "No features currently in progress.",
            "In Review": "No features currently in review.",
            "Backlog": "No features currently in the backlog.",
            "Done": "No features currently in the backlog.",
        }[section]
    return f"\n{prefix}\n"


def blocks_by_section(registry: str) -> dict[str, list[str]]:
    grouped = {section: [] for section in SECTIONS}
    for match in feature_blocks(registry).values():
        section = section_for_position(registry, match.start())
        if section is None:
            raise OperationError(f"Feature {match.group(1)} is outside a lifecycle section.")
        grouped[section].append(match.group(0).rstrip())
    return grouped


def write_registry_sections(registry: str, grouped: dict[str, list[str]]) -> str:
    updated = registry
    for section in reversed(SECTIONS):
        updated = rebuild_section(updated, section, grouped[section])
    return updated


def block_for(registry: str, identifier: str) -> str:
    match = feature_blocks(registry).get(identifier)
    if not match:
        raise OperationError(f"Feature not found: {identifier}")
    return match.group(0).rstrip()


def replace_block(registry: str, identifier: str, replacement: str) -> str:
    match = feature_blocks(registry).get(identifier)
    if not match:
        raise OperationError(f"Feature not found: {identifier}")
    suffix = registry[match.end() :].lstrip("\n")
    separator = "\n\n" if suffix else "\n"
    return registry[: match.start()] + replacement.rstrip() + separator + suffix


def block_status(block: str) -> str:
    match = STATUS_RE.search(block)
    if not match or match.group(2) not in STATUSES:
        raise OperationError("Feature block has no valid status line.")
    return match.group(2)


def append_note(path: Path, note: str) -> None:
    details = read_utf8(path)
    if "# Notes" not in details:
        details = details.rstrip() + "\n\n# Notes\n"
    if not details.endswith("\n"):
        details += "\n"
    atomic_write(path, details.rstrip() + f"\n- {note}\n")


def parse_acceptance(values: list[str] | None, file_path: str | None) -> list[str]:
    criteria = list(values or [])
    if file_path:
        criteria.extend(
            line.strip()[2:].strip()
            for line in Path(file_path).read_text(encoding="utf-8").splitlines()
            if line.strip().startswith("- ")
        )
    criteria = [criterion for criterion in criteria if criterion]
    if not criteria:
        raise OperationError("At least one acceptance criterion is required.")
    return criteria


def command_create(args: argparse.Namespace) -> None:
    root = Path(args.root)
    registry_file = registry_path(root)
    registry = read_utf8(registry_file)
    used = set(feature_blocks(registry))
    used.update(
        path.stem.removesuffix("-DETAILS")
        for path in (root / "docs" / "sdd").glob("FEAT-*-DETAILS.md")
    )
    next_number = max((int(identifier[5:]) for identifier in used), default=0) + 1
    identifier = f"FEAT-{next_number:05d}"
    criteria = parse_acceptance(args.acceptance, args.acceptance_file)
    dependency = ", ".join(args.depends_on) if args.depends_on else "—"
    block = (
        f"### [{identifier}] {args.title}\n"
        "- **Status:** idea\n"
        f"- **Priority:** {args.priority}\n"
        f"- **Effort:** {args.effort}\n"
        f"- **Depends on:** {dependency}\n\n"
        "#### Goal\n"
        f"{args.goal}\n\n"
        "#### Acceptance Criteria\n"
        + "\n".join(f"- [ ] {criterion}" for criterion in criteria)
        + "\n\n"
        "#### Feature Details\n"
        f"→ See [{identifier}-DETAILS.md](./{identifier}-DETAILS.md) "
        "for a detailed breakdown of the feature, build plan, and implementation steps.\n"
    )
    grouped = blocks_by_section(registry)
    grouped["Backlog"].append(block)
    updated = write_registry_sections(registry, grouped)
    index_marker = "## Main index\n"
    if index_marker not in updated:
        raise OperationError("Missing ## Main index in FEATURES.md.")
    index_entry = f"- [{identifier}: {args.title}](### [{identifier}] {args.title})\n"
    updated = updated.replace(index_marker, index_marker + "\n" + index_entry, 1)
    details = (
        "# Build Plan\n"
        "> Fill this in when status moves to `planned`.\n\n"
        "# Implementation Guidelines\n\n"
        "# Notes\n"
    )
    details_file = details_path(root, identifier)
    if details_file.exists():
        raise OperationError(f"Details file already exists: {details_file}")
    atomic_write(registry_file, updated)
    atomic_write(details_file, details)
    print(identifier)


def command_status(args: argparse.Namespace) -> None:
    root = Path(args.root)
    identifier = feature_id(args.id)
    registry_file = registry_path(root)
    registry = read_utf8(registry_file)
    current_block = block_for(registry, identifier)
    current_status = block_status(current_block)
    target = args.status
    if target == "done":
        if not args.confirm_done:
            raise OperationError("Closing a feature requires --confirm-done.")
        if re.search(r"^- \[ \] ", current_block, re.MULTILINE):
            if not args.check_acceptance:
                raise OperationError(
                    "Closing a feature with unchecked criteria requires --check-acceptance."
                )
            current_block = re.sub(r"^- \[ \] ", "- [x] ", current_block, flags=re.MULTILINE)
    if target == "blocked" and not args.note:
        raise OperationError("A blocked feature requires --note.")
    if current_status == target:
        raise OperationError(f"{identifier} is already {target}.")
    replacement = STATUS_RE.sub(rf"\g<1>{target}", current_block, count=1)
    grouped = blocks_by_section(replace_block(registry, identifier, ""))
    current_section = STATUS_SECTION[current_status]
    grouped[current_section] = [
        block for block in grouped[current_section] if identifier not in block
    ]
    grouped[STATUS_SECTION[target]].append(replacement)
    updated = write_registry_sections(registry, grouped)
    atomic_write(registry_file, updated)
    if args.note:
        append_note(details_path(root, identifier), args.note)


def replace_heading_section(details: str, heading: str, replacement: str, next_headings: tuple[str, ...]) -> str:
    match = re.search(rf"(?m)^{re.escape(heading)}[ \t]*$", details)
    if not match:
        raise OperationError(f"Details file is missing {heading}.")
    end = len(details)
    for next_heading in next_headings:
        next_match = re.search(rf"(?m)^{re.escape(next_heading)}[ \t]*$", details[match.end() :])
        if next_match:
            end = min(end, match.end() + next_match.start())
    return details[: match.end()] + "\n" + replacement.rstrip() + "\n\n" + details[end:].lstrip("\n")


def command_plan(args: argparse.Namespace) -> None:
    root = Path(args.root)
    identifier = feature_id(args.id)
    plan_file = Path(args.file)
    if not plan_file.exists():
        raise OperationError(f"Plan input file not found: {plan_file}")
    plan = plan_file.read_text(encoding="utf-8").strip()
    embedded_plan = re.search(r"(?ms)^# Build Plan[ \t]*\n(.*?)(?=^# |\Z)", plan)
    if embedded_plan:
        plan = embedded_plan.group(1).strip()
    if not re.search(r"(?m)^\s*(?:\d+\.|-)\s+\S+", plan):
        raise OperationError("Plan must contain at least one list step.")
    details_file = details_path(root, identifier)
    details = read_utf8(details_file)
    updated_details = replace_heading_section(
        details,
        "# Build Plan",
        plan,
        ("# Implementation Guidelines", "# Notes"),
    )
    atomic_write(details_file, updated_details)
    if args.mark_planned:
        status_args = argparse.Namespace(
            root=args.root,
            id=identifier,
            status="planned",
            confirm_done=False,
            note=None,
        )
        command_status(status_args)


def command_update(args: argparse.Namespace) -> None:
    root = Path(args.root)
    identifier = feature_id(args.id)
    registry_file = registry_path(root)
    registry = read_utf8(registry_file)
    block = block_for(registry, identifier)
    changed = False
    if args.goal:
        block = replace_heading_section(block, "#### Goal", args.goal, ("#### Acceptance Criteria", "#### Feature Details"))
        changed = True
    for label, value in (("Priority", args.priority), ("Effort", args.effort)):
        if value:
            pattern = rf"^(- \*\*{label}:\*\* ).+$"
            block, count = re.subn(pattern, rf"\g<1>{value}", block, count=1, flags=re.MULTILINE)
            if count != 1:
                raise OperationError(f"Feature block is missing {label}.")
            changed = True
    if args.depends_on is not None:
        dependency = ", ".join(args.depends_on) if args.depends_on else "—"
        block, count = re.subn(
            r"^(- \*\*Depends on:\*\* ).+$",
            rf"\g<1>{dependency}",
            block,
            count=1,
            flags=re.MULTILINE,
        )
        if count != 1:
            raise OperationError("Feature block is missing Depends on.")
        changed = True
    if args.acceptance or args.acceptance_file:
        criteria = parse_acceptance(args.acceptance, args.acceptance_file)
        acceptance = "\n".join(f"- [ ] {criterion}" for criterion in criteria)
        block = replace_heading_section(block, "#### Acceptance Criteria", acceptance, ("#### Feature Details",))
        changed = True
    if not changed:
        raise OperationError("Provide at least one field to update.")
    atomic_write(registry_file, replace_block(registry, identifier, block))


def command_validate(args: argparse.Namespace) -> None:
    root = Path(args.root)
    registry_file = registry_path(root)
    registry = read_utf8(registry_file)
    errors: list[str] = []
    matches = list(FEATURE_RE.finditer(registry))
    identifiers = [match.group(1) for match in matches]
    blocks = {match.group(1): match for match in matches}
    if len(identifiers) != len(set(identifiers)):
        errors.append("Duplicate feature IDs exist.")
    grouped = blocks_by_section(registry)
    for identifier, match in blocks.items():
        block = match.group(0)
        status = block_status(block)
        expected_section = STATUS_SECTION[status]
        actual_section = section_for_position(registry, match.start())
        if actual_section != expected_section:
            errors.append(f"{identifier}: status {status} belongs under {expected_section}.")
        if (
            f"({identifier}-DETAILS.md)" not in block
            and f"(./{identifier}-DETAILS.md)" not in block
        ):
            errors.append(f"{identifier}: missing matching details link.")
        details_file = details_path(root, identifier)
        if not details_file.exists():
            errors.append(f"{identifier}: missing {details_file.name}.")
        elif status in ("planned", "ready", "in-progress", "in-review", "done"):
            details = read_utf8(details_file)
            plan_match = re.search(r"(?ms)^# Build Plan[ \t]*\n(.*?)(?=^# |\Z)", details)
            if not plan_match or not re.search(r"(?m)^\s*(?:\d+\.|-)\s+\S+", plan_match.group(1)):
                errors.append(f"{identifier}: missing a concrete build-plan step.")
        if status == "blocked":
            details = read_utf8(details_file) if details_file.exists() else ""
            notes = details.split("# Notes", 1)[-1]
            if not notes.strip() or not re.search(r"(?m)^-\s+\S+", notes):
                errors.append(f"{identifier}: blocked status requires a note.")
        if f"[{identifier}:" not in registry.split("## In Progress", 1)[0]:
            errors.append(f"{identifier}: missing from ## Main index.")
    done_ids = [re.match(r"^### \[(FEAT-\d{5})\]", block).group(1) for block in grouped["Done"]]
    if done_ids != sorted(done_ids, reverse=True):
        errors.append("Done features are not ordered by descending feature ID.")
    if errors:
        raise OperationError("\n".join(errors))
    print(f"OK: {len(blocks)} features validated.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(repository_root()), help="Repository root.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    create = subparsers.add_parser("create", help="Create a new idea feature.")
    create.add_argument("--title", required=True)
    create.add_argument("--goal", required=True)
    create.add_argument("--priority", choices=("low", "medium", "high"), default="medium")
    create.add_argument("--effort", choices=("small", "medium", "large"), default="medium")
    create.add_argument("--depends-on", action="append", default=[])
    create.add_argument("--acceptance", action="append")
    create.add_argument("--acceptance-file")
    create.set_defaults(function=command_create)

    status = subparsers.add_parser("status", help="Move a feature to another lifecycle status.")
    status.add_argument("--id", required=True)
    status.add_argument("--status", required=True, choices=STATUSES)
    status.add_argument("--note")
    status.add_argument("--confirm-done", action="store_true")
    status.add_argument("--check-acceptance", action="store_true")
    status.set_defaults(function=command_status)

    update = subparsers.add_parser("update", help="Update registry metadata or acceptance criteria.")
    update.add_argument("--id", required=True)
    update.add_argument("--goal")
    update.add_argument("--priority", choices=("low", "medium", "high"))
    update.add_argument("--effort", choices=("small", "medium", "large"))
    update.add_argument("--depends-on", action="append")
    update.add_argument("--acceptance", action="append")
    update.add_argument("--acceptance-file")
    update.set_defaults(function=command_update)

    plan = subparsers.add_parser("plan", help="Replace a feature's build plan.")
    plan.add_argument("--id", required=True)
    plan.add_argument("--file", required=True)
    plan.add_argument("--mark-planned", action="store_true")
    plan.set_defaults(function=command_plan)

    validate = subparsers.add_parser("validate", help="Validate registry and details invariants.")
    validate.set_defaults(function=command_validate)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        args.function(args)
    except OperationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
