#!/usr/bin/env python3
"""Recover Macrobenchmark results from a Sauce Labs device log and compare the two builds.

Sauce Labs cannot pull arbitrary files off a real device, so SentryStartupBenchmark echoes its
`<pkg>-benchmarkData.json` into logcat as numbered chunks. This reassembles those chunks and
prints a Markdown summary.

When the run alternated between the base build and the build under test, the summary is a
base-vs-PR comparison. When only one build was installed -- a plain local run -- it falls back to
reporting that build on its own.

Usage:
    parse-macrobenchmark-log.py <artifacts-dir> [--json-out benchmarkData.json]
                                [--base-sha SHA] [--head-sha SHA]
"""

import argparse
import json
import random
import re
import statistics
import sys
from pathlib import Path

# Must match SentryStartupBenchmark.LOG_TAG and its "[index/total]" chunk prefix.
CHUNK_RE = re.compile(r"SentryBenchmarkData\s*:\s*\[(\d+)/(\d+)\](.*)$")

# Must match SentryStartupBenchmark.Variant. These appear in each benchmark's parameterized name,
# e.g. `startup[03-candidate]`.
BASELINE, CANDIDATE = "baseline", "candidate"

# Enough resamples that the reported p is stable to about a thousandth, and still well under a
# second for the ~24 measurements a run produces.
RESAMPLES = 10000

# Fixed so re-running the parser on the same log always prints the same p.
SEED = 0


def log_messages(log_file):
    """Yields the message text of every log entry.

    Sauce hands back device.log as JSON lines -- {"tag", "message", "level", ...} -- which
    means the payload arrives with its quotes escaped, so it has to be decoded rather than
    regexed out of the raw line. Plain-text lines are passed through unchanged so the same
    parser works on `adb logcat` output from a local run.
    """
    # Sauce device logs occasionally carry undecodable bytes; don't die on them.
    for line in log_file.read_text(errors="replace").splitlines():
        line = line.strip()
        if line.startswith("{"):
            try:
                yield json.loads(line).get("message", "")
                continue
            except json.JSONDecodeError:
                pass
        yield line


def collect_chunks(log_file):
    """Returns one log's chunk texts keyed by index, plus the expected total.

    The benchmark may dump its results more than once (see logBenchmarkDataToLogcat). Later
    chunks overwrite earlier ones and the file only ever grows, so what survives is the last,
    most complete document.
    """
    chunks, total = {}, None
    for message in log_messages(log_file):
        match = CHUNK_RE.search(message)
        if match:
            index, total = int(match.group(1)), int(match.group(2))
            chunks[index] = match.group(3)
    return chunks, total


def reassemble(chunks, total):
    missing = [i for i in range(1, total + 1) if i not in chunks]
    if missing:
        sys.exit(f"Incomplete benchmark data: missing chunk(s) {missing} of {total}")
    return "".join(chunks[i] for i in range(1, total + 1))


def variant_of(benchmark_name):
    """Which build a benchmark entry measured, or None if the run wasn't labelled."""
    for variant in (BASELINE, CANDIDATE):
        if variant in benchmark_name:
            return variant
    return None


def pool_runs(benchmarks):
    """Returns {variant: {metric: [every iteration, across all steps]}}.

    Each step's own median and coefficientOfVariation are computed over a handful of iterations
    and say nothing useful, so the per-iteration values are pooled per variant and the statistics
    recomputed from those.
    """
    pooled = {}
    for benchmark in benchmarks:
        variant = variant_of(benchmark["name"])
        for metric, result in benchmark["metrics"].items():
            pooled.setdefault(variant, {}).setdefault(metric, []).extend(result["runs"])
    return pooled


def summarize(runs):
    return {
        "min": min(runs),
        "median": statistics.median(runs),
        "max": max(runs),
        "cov": statistics.stdev(runs) / statistics.fmean(runs) if len(runs) > 1 else 0.0,
        "n": len(runs),
    }


def permutation_p(base_runs, candidate_runs):
    """Two-sided p-value for "the medians differ", by relabelling the measurements at random.

    Distribution-free, which suits a dozen noisy cold starts per arm better than a t-test: it
    asks how often shuffling the same measurements between the two labels produces a median gap
    at least as big as the one actually observed. A large p means the split we saw is an
    unremarkable way to deal out these numbers -- not evidence of no change, just no evidence of
    one.
    """
    observed = abs(statistics.median(candidate_runs) - statistics.median(base_runs))
    pool = list(base_runs) + list(candidate_runs)
    split = len(base_runs)
    rng = random.Random(SEED)
    at_least_as_extreme = 0
    for _ in range(RESAMPLES):
        rng.shuffle(pool)
        gap = abs(statistics.median(pool[split:]) - statistics.median(pool[:split]))
        if gap >= observed:
            at_least_as_extreme += 1
    # Add-one keeps p away from an unachievable 0.
    return (at_least_as_extreme + 1) / (RESAMPLES + 1)


def format_header(data, base_sha, head_sha):
    context = data["context"]
    build = context["build"]
    lines = [
        f"**Device:** {build['brand']} {build['model']} "
        f"(api {build['version']['sdk']}, {context['cpuCoreCount']} cores) &middot; "
        f"**compilation:** {context['compilationMode']} &middot; "
        f"**CPU clocks locked:** {context['cpuLocked']}",
        "",
    ]
    if base_sha or head_sha:
        lines[:0] = [
            f"**base:** `{base_sha or 'unknown'}` &rarr; **PR:** `{head_sha or 'unknown'}`",
            "",
        ]
    return lines


def format_runs_details(label, runs):
    return [
        "",
        f"<details><summary>{label} per iteration</summary>",
        "",
        ", ".join(f"{run:.1f}" for run in runs),
        "",
        "</details>",
    ]


def format_comparison(data, pooled, base_sha, head_sha):
    lines = ["## Macrobenchmark: PR vs base", ""]
    lines += format_header(data, base_sha, head_sha)

    if not data["context"]["cpuLocked"]:
        lines += [
            "> CPU clocks are unlocked on this device, so the absolute numbers run high and wide. "
            "The two builds were measured alternately on the same device in the same session, so "
            "the delta is the trustworthy part -- not the medians either side of it.",
            "",
        ]

    lines += [
        "| Metric | base median | PR median | Δ | Δ% | p | base min–max (CoV) | PR min–max (CoV) |",
        "|---|--:|--:|--:|--:|--:|--:|--:|",
    ]
    details = []
    for metric in sorted(set(pooled[BASELINE]) & set(pooled[CANDIDATE])):
        # Only metrics both builds reported can be compared; the rest are called out below.
        base_runs, candidate_runs = pooled[BASELINE][metric], pooled[CANDIDATE][metric]
        base, candidate = summarize(base_runs), summarize(candidate_runs)
        delta = candidate["median"] - base["median"]
        delta_pct = delta / base["median"] * 100 if base["median"] else 0.0
        lines.append(
            f"| {metric} | {base['median']:.1f} | {candidate['median']:.1f} "
            f"| {delta:+.1f} | {delta_pct:+.1f}% "
            f"| {permutation_p(base_runs, candidate_runs):.3f} "
            f"| {base['min']:.1f}–{base['max']:.1f} ({base['cov'] * 100:.1f}%) "
            f"| {candidate['min']:.1f}–{candidate['max']:.1f} ({candidate['cov'] * 100:.1f}%) |"
        )
        details += format_runs_details(f"{metric} — base", base_runs)
        details += format_runs_details(f"{metric} — PR", candidate_runs)

    iterations = len(next(iter(pooled[CANDIDATE].values())))
    lines += [
        "",
        f"Δ is PR minus base, so negative is faster. {iterations} cold starts per build, "
        "alternating between them so thermal drift lands on both. `p` is a permutation test on "
        "the difference of medians: small means the gap is hard to explain as a reshuffle of the "
        "same measurements. Reported for information only — this job never fails on it.",
    ]

    one_sided = sorted(set(pooled[BASELINE]) ^ set(pooled[CANDIDATE]))
    if one_sided:
        lines += [
            "",
            "> Only one build reported " + ", ".join(f"`{m}`" for m in one_sided) + ", so "
            "there is nothing to compare it against and it is missing from the table above.",
        ]

    if pooled.get(None):
        lines += [
            "",
            "> Some results carried no variant label and were left out of the table. That means "
            "the benchmark ran a test this parser doesn't know about.",
        ]

    return "\n".join(lines + details)


def format_single(data, pooled, base_sha, head_sha):
    """Fallback for a run that measured one build, e.g. a local connectedBenchmarkAndroidTest."""
    variant, metrics = next(iter(pooled.items()))
    lines = ["## Macrobenchmark results", ""]
    lines += format_header(data, base_sha, head_sha)
    lines += [
        f"> Only the **{variant or 'unlabelled'}** build was measured, so there is nothing to "
        "compare against. Install both builds to get a base-vs-PR table.",
        "",
        "| Metric | min | median | max | CoV | iterations |",
        "|---|--:|--:|--:|--:|--:|",
    ]
    details = []
    for metric in sorted(metrics):
        runs = metrics[metric]
        stats = summarize(runs)
        lines.append(
            f"| {metric} | {stats['min']:.1f} | {stats['median']:.1f} | {stats['max']:.1f} "
            f"| {stats['cov'] * 100:.1f}% | {stats['n']} |"
        )
        details += format_runs_details(metric, runs)
    return "\n".join(lines + details)


def format_summary(data, base_sha=None, head_sha=None):
    pooled = pool_runs(data["benchmarks"])
    if not pooled:
        sys.exit("Benchmark data contains no results")
    if BASELINE in pooled and CANDIDATE in pooled:
        return format_comparison(data, pooled, base_sha, head_sha)
    return format_single(data, pooled, base_sha, head_sha)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifacts_dir", type=Path, help="directory of downloaded Sauce artifacts")
    parser.add_argument("--json-out", type=Path, help="where to write the recovered benchmarkData.json")
    parser.add_argument("--base-sha", help="commit the baseline build came from")
    parser.add_argument("--head-sha", help="commit the build under test came from")
    args = parser.parse_args()

    log_files = sorted(args.artifacts_dir.rglob("*.log"))
    if not log_files:
        sys.exit(f"No *.log files under {args.artifacts_dir}")

    # Keyed by file so chunks from two devices can never be merged into one bogus document.
    per_log = {log: collect_chunks(log) for log in log_files}
    with_chunks = {log: result for log, (result, total) in per_log.items() if total}
    if not with_chunks:
        sys.exit(
            "No SentryBenchmarkData chunks in the device log. The benchmark most likely "
            "failed before reporting — check junit.xml and the log for Macrobenchmark errors."
        )
    if len(with_chunks) > 1:
        sys.exit(
            "Chunks from more than one run: "
            + ", ".join(str(log) for log in with_chunks)
            + ". This parser reports a single device."
        )
    log_file = next(iter(with_chunks))
    chunks, total = per_log[log_file]

    data = json.loads(reassemble(chunks, total))

    if args.json_out:
        args.json_out.write_text(json.dumps(data, indent=2))

    print(format_summary(data, args.base_sha, args.head_sha))


if __name__ == "__main__":
    main()
