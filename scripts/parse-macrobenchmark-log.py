#!/usr/bin/env python3
"""Recover Macrobenchmark results from a Sauce Labs device log.

Sauce Labs cannot pull arbitrary files off a real device, so
SentryStartupBenchmark echoes its `<pkg>-benchmarkData.json` into logcat as
numbered chunks. This reassembles those chunks and prints a Markdown summary.

Usage:
    parse-macrobenchmark-log.py <artifacts-dir> [--json-out benchmarkData.json]
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Must match SentryStartupBenchmark.LOG_TAG and its "[index/total]" chunk prefix.
CHUNK_RE = re.compile(r"SentryBenchmarkData\s*:\s*\[(\d+)/(\d+)\](.*)$")


def collect_chunks(log_files):
    """Returns the chunk texts keyed by index, plus the expected total."""
    chunks = {}
    total = None
    for log_file in log_files:
        # Sauce device logs occasionally carry undecodable bytes; don't die on them.
        for line in log_file.read_text(errors="replace").splitlines():
            match = CHUNK_RE.search(line)
            if not match:
                continue
            index, chunk_total, text = int(match.group(1)), int(match.group(2)), match.group(3)
            if total is not None and chunk_total != total:
                sys.exit(
                    f"Found chunks from more than one benchmark run "
                    f"(totals {total} and {chunk_total}) in {log_file}"
                )
            total = chunk_total
            chunks[index] = text
    return chunks, total


def reassemble(chunks, total):
    missing = [i for i in range(1, total + 1) if i not in chunks]
    if missing:
        sys.exit(f"Incomplete benchmark data: missing chunk(s) {missing} of {total}")
    return "".join(chunks[i] for i in range(1, total + 1))


def format_summary(data):
    context = data["context"]
    build = context["build"]
    lines = [
        "## Macrobenchmark results",
        "",
        f"**Device:** {build['brand']} {build['model']} "
        f"(api {build['version']['sdk']}, {context['cpuCoreCount']} cores) &middot; "
        f"**compilation:** {context['compilationMode']} &middot; "
        f"**CPU clocks locked:** {context['cpuLocked']}",
        "",
    ]

    if not context["cpuLocked"]:
        lines += [
            "> CPU clocks are unlocked on this device, so run-to-run spread is wide. "
            "Treat these numbers as a trend, not a regression gate.",
            "",
        ]

    lines += ["| Benchmark | Metric | min | median | max | CoV | iterations |", "|---|---|--:|--:|--:|--:|--:|"]
    for benchmark in data["benchmarks"]:
        for metric, result in sorted(benchmark["metrics"].items()):
            lines.append(
                f"| `{benchmark['className'].rsplit('.', 1)[-1]}.{benchmark['name']}` "
                f"| {metric} "
                f"| {result['minimum']:.1f} "
                f"| {result['median']:.1f} "
                f"| {result['maximum']:.1f} "
                f"| {result['coefficientOfVariation'] * 100:.1f}% "
                f"| {len(result['runs'])} |"
            )

    for benchmark in data["benchmarks"]:
        for metric, result in sorted(benchmark["metrics"].items()):
            runs = ", ".join(f"{run:.1f}" for run in result["runs"])
            lines += ["", f"<details><summary>{metric} per iteration</summary>", "", runs, "", "</details>"]

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifacts_dir", type=Path, help="directory of downloaded Sauce artifacts")
    parser.add_argument("--json-out", type=Path, help="where to write the recovered benchmarkData.json")
    args = parser.parse_args()

    log_files = sorted(args.artifacts_dir.rglob("*.log"))
    if not log_files:
        sys.exit(f"No *.log files under {args.artifacts_dir}")

    chunks, total = collect_chunks(log_files)
    if total is None:
        sys.exit(
            "No SentryBenchmarkData chunks in the device log. The benchmark most likely "
            "failed before reporting — check junit.xml and the log for Macrobenchmark errors."
        )

    data = json.loads(reassemble(chunks, total))

    if args.json_out:
        args.json_out.write_text(json.dumps(data, indent=2))

    print(format_summary(data))


if __name__ == "__main__":
    main()
