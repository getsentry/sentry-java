#!/usr/bin/env python3
"""Run native crash scenarios on an installed Android sample and report CSV timings."""

import argparse
import csv
import queue
import re
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path

SCENARIOS = (
    "baseline",
    "out_of_memory",
    "locked_heap",
    "corrupted_heap",
    "no_file_descriptors",
    "stack_corruption",
    "stack_overflow",
    "concurrent",
    "many_threads",
)
LOG_ENTRY = re.compile(r"^\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+[VDIWEF]\s+([^:]+):\s*(.*)$")


def adb_command(adb, *args):
    result = subprocess.run(
        adb + list(args), capture_output=True, text=True, check=True
    )
    if "Error:" in result.stdout or "Exception" in result.stdout:
        raise RuntimeError(result.stdout.strip())
    return result.stdout


def read_logcat(process, lines):
    for line in process.stdout:
        lines.put(line)
    lines.put(None)


def wait_for_death(lines, log, package, scenario, run_id, timeout):
    deadline = time.monotonic() + timeout
    marker = f"scenario={scenario} run_id={run_id}"
    death = re.compile(rf"Process {re.escape(package)} \(pid (\d+)\) has died")
    pid = None
    fault_time = None
    while time.monotonic() < deadline:
        try:
            line = lines.get(timeout=max(0.01, deadline - time.monotonic()))
        except queue.Empty:
            break
        if line is None:
            return pid, "logcat_stopped", None
        log.write(line)
        log.flush()
        entry = LOG_ENTRY.match(line)
        if not entry:
            continue
        timestamp, entry_pid, tag, message = entry.groups()
        if tag.strip() == "SentryCrashScenario" and marker in message:
            pid = int(entry_pid)
            if message.startswith("status=error "):
                return pid, "setup_error", None
            if message.startswith("status=fault ") and fault_time is None:
                fault_time = float(timestamp)
        elif tag.strip() == "ActivityManager":
            match = death.search(message)
            if match and int(match.group(1)) == pid:
                if fault_time is None:
                    return pid, "died_before_fault", None
                return pid, "died", (float(timestamp) - fault_time) * 1000
    return pid, "death_timeout" if fault_time is not None else "fault_timeout", None


def run_scenario(adb, args, scenario, run_id):
    adb_command(adb, "shell", "am", "force-stop", args.package)
    log_path = args.output_dir / f"{run_id}.log"
    process = subprocess.Popen(
        adb
        + [
            "logcat",
            "-b",
            "main",
            "-b",
            "system",
            "-b",
            "crash",
            "-v",
            "epoch",
            "-T",
            "1",
            "SentryCrashScenario:V",
            "ActivityManager:I",
            "libc:V",
            "DEBUG:V",
            "Sentry:V",
            "*:S",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    lines = queue.Queue()
    reader = threading.Thread(target=read_logcat, args=(process, lines), daemon=True)
    reader.start()
    try:
        with log_path.open("w") as log:
            adb_command(
                adb,
                "shell",
                "am",
                "start",
                "-n",
                f"{args.package}/io.sentry.samples.android.NativeCrashActivity",
                "--es",
                "scenario",
                scenario,
                "--es",
                "run_id",
                run_id,
            )
            pid, status, elapsed = wait_for_death(
                lines, log, args.package, scenario, run_id, args.timeout
            )
            if status != "died":
                adb_command(adb, "shell", "am", "force-stop", args.package)
            adb_command(
                adb,
                "shell",
                "am",
                "start",
                "-n",
                f"{args.package}/io.sentry.samples.android.MainActivity",
            )
            time.sleep(args.delivery_wait)
            while not lines.empty():
                line = lines.get_nowait()
                if line is not None:
                    log.write(line)
            return (
                scenario,
                run_id,
                pid,
                status,
                f"{elapsed:.3f}" if elapsed is not None else "",
                log_path,
            )
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        reader.join(timeout=5)
        process.stdout.close()


def positive_int(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be at least 1")
    return number


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--serial",
        help="adb device serial, required when multiple devices are connected",
    )
    parser.add_argument(
        "--package",
        default="io.sentry.samples.android",
        help="installed application ID",
    )
    parser.add_argument("--scenario", nargs="+", choices=SCENARIOS, default=SCENARIOS)
    parser.add_argument("--runs", type=positive_int, default=10)
    parser.add_argument(
        "--timeout",
        type=positive_int,
        default=60,
        help="seconds to wait for process death",
    )
    parser.add_argument(
        "--delivery-wait",
        type=positive_int,
        default=10,
        help="seconds to allow next-launch delivery",
    )
    parser.add_argument("--output-dir", type=Path, default=Path("crash-scenario-logs"))
    args = parser.parse_args()
    adb = ["adb"] + (["-s", args.serial] if args.serial else [])
    try:
        adb_command(adb, "get-state")
        args.output_dir.mkdir(parents=True, exist_ok=True)
        writer = csv.writer(sys.stdout)
        writer.writerow(
            ("scenario", "run_id", "pid", "status", "fault_to_death_ms", "log")
        )
        failed = False
        for scenario in args.scenario:
            for _ in range(args.runs):
                run_id = f"{scenario}-{uuid.uuid4().hex}"
                result = run_scenario(adb, args, scenario, run_id)
                writer.writerow(result)
                sys.stdout.flush()
                failed |= result[3] != "died"
        return 1 if failed else 0
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
