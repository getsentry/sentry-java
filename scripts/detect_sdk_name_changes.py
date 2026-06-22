#!/usr/bin/env python3
"""Detect added and removed sdk.name values in Config.kt between two revisions."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

CONFIG_PATH = "buildSrc/src/main/java/Config.kt"
SDK_MAP_SHEET_URL = (
    "https://docs.google.com/spreadsheets/d/1hqFhytQuHMvuOz1XD0kCXg6x0ViflHrpjW7nNhvzYmU/"
    "edit?gid=334165604"
)
SDK_NAME_PATTERN = re.compile(r"^\s*val (\w+SDK_NAME) = (.+)$")
STRING_LITERAL = re.compile(r'^"((?:[^"\\]|\\.)*)"$')
INTERPOLATION_PATTERN = re.compile(r"\$([A-Za-z0-9_]+)")


def parse_sdk_constants(content: str) -> dict[str, str]:
    """Return mapping of constant name to resolved sdk.name string."""
    resolved: dict[str, str] = {}
    for line in content.splitlines():
        match = SDK_NAME_PATTERN.match(line)
        if not match:
            continue
        name, rhs = match.group(1), match.group(2).strip()
        resolved[name] = resolve_rhs(rhs, resolved)
    return resolved


def resolve_rhs(rhs: str, resolved: dict[str, str]) -> str:
    literal = STRING_LITERAL.match(rhs)
    if not literal:
        raise ValueError(f"Cannot parse SDK name assignment: {rhs}")
    return expand_interpolation(literal.group(1), resolved)


def expand_interpolation(value: str, resolved: dict[str, str]) -> str:
    if "${" in value:
        raise ValueError(f"Unsupported brace interpolation in SDK name value: {value}")

    def replace(match: re.Match[str]) -> str:
        name = match.group(1)
        if name not in resolved:
            raise ValueError(f"Unknown SDK name constant: {name}")
        return resolved[name]

    return INTERPOLATION_PATTERN.sub(replace, value)


def find_sdk_name_changes(
    base: dict[str, str], head: dict[str, str]
) -> tuple[list[str], list[str]]:
    base_values = set(base.values())
    head_values = set(head.values())
    added = sorted(head_values - base_values)
    removed = sorted(base_values - head_values)
    return added, removed


def git_show(ref: str, path: str) -> str:
    try:
        return subprocess.check_output(
            ["git", "show", f"{ref}:{path}"],
            text=True,
            stderr=subprocess.PIPE,
        )
    except subprocess.CalledProcessError as error:
        if error.returncode == 128 and "exists on disk, but not in" in error.stderr:
            return ""
        raise


def read_config_source(base: str | None, head: str | None, config_path: str) -> tuple[str, str]:
    if base is None or head is None:
        raise ValueError("Both base and head refs are required")
    return git_show(base, config_path), git_show(head, config_path)


def format_changes(added: list[str], removed: list[str]) -> str:
    return json.dumps(
        {
            "added": added,
            "removed": removed,
            "sdk_map_sheet_url": SDK_MAP_SHEET_URL,
        },
        separators=(",", ":"),
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Print added and removed sdk.name values as JSON."
    )
    parser.add_argument("--base", help="Git ref for the PR base revision")
    parser.add_argument("--head", help="Git ref for the PR head revision")
    parser.add_argument("--base-file", type=Path, help="Base Config.kt file")
    parser.add_argument("--head-file", type=Path, help="Head Config.kt file")
    parser.add_argument("--config-path", default=CONFIG_PATH)
    args = parser.parse_args(argv)

    try:
        if args.base_file and args.head_file:
            base_content = args.base_file.read_text(encoding="utf-8")
            head_content = args.head_file.read_text(encoding="utf-8")
        elif args.base and args.head:
            base_content, head_content = read_config_source(
                args.base, args.head, args.config_path
            )
        else:
            parser.error("Provide --base/--head or --base-file/--head-file")

        base_sdk = parse_sdk_constants(base_content)
        head_sdk = parse_sdk_constants(head_content)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as error:
        print(f"error: git command failed: {' '.join(error.cmd)}", file=sys.stderr)
        return 1

    added, removed = find_sdk_name_changes(base_sdk, head_sdk)
    print(format_changes(added, removed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
