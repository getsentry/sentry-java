#!/usr/bin/env python3

"""
Run the Android Macrobenchmark on a Sauce Labs real device and pull its results back.

Why this exists instead of a saucectl espresso suite: saucectl can only download the
assets Sauce itself hosts for a job -- device.log, junit.xml, video.mp4, network.har,
crash.json, screenshots.zip -- and never reads the device filesystem. Macrobenchmark
writes its numbers to `<pkg>-benchmarkData.json` and its per-iteration perfetto traces
into on-device storage, so under saucectl they stay stranded on the device.

The Real Device Access API (open beta) does expose the device: it offers adb shell and
a pullFile endpoint. So this script drives the run itself -- reserve a device, install
both APKs, run the instrumentation, pull the results -- which also gets us the perfetto
traces, the only way to resolve sub-millisecond SDK changes that timeToInitialDisplay
cannot see.

Usage:
  # Just check whether this account has Real Device Access API entitlement
  python3 scripts/macrobenchmark-sauce.py --probe-only

  # Faster smoke test: same iterations, but no AOT compilation
  python3 scripts/macrobenchmark-sauce.py \
      --app sentry-samples/sentry-samples-android/build/outputs/apk/release/sentry-samples-android-release.apk \
      --test-app sentry-android-integration-tests/sentry-uitest-android-macrobenchmark/build/outputs/apk/benchmark/sentry-uitest-android-macrobenchmark-benchmark.apk \
      --skip-compilation

  # Full run, as the benchmark declares it
  python3 scripts/macrobenchmark-sauce.py --app <apk> --test-app <apk>

Requires SAUCE_USERNAME and SAUCE_ACCESS_KEY in the environment.
"""

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path

import requests

TEST_PACKAGE = "io.sentry.uitest.android.macrobenchmark"
TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

# Macrobenchmark's default output dir. Deliberately not overridden via
# additionalTestOutputDir: on API 29+ the test process cannot write outside its own
# scoped directories, and this media dir is the one place both the app and the shell
# can reach -- which is exactly why androidx.benchmark picked it (see b/181601156),
# and it is what makes it reachable via pullFile.
DEVICE_OUTPUT_DIR = f"/sdcard/Android/media/{TEST_PACKAGE}"

# Shell-owned scratch space for the instrumentation's own stdout and exit code.
SHELL_SCRATCH_DIR = "/data/local/tmp/macrobenchmark"

# Matched as a regex against descriptor ids and names. Kept loose on purpose: Real Device
# Access descriptors are not the ids used in .sauce/*.yml, which carry an OS version and a
# region suffix (Google_Pixel_9_Pro_XL_15_real_sjc1).
DEFAULT_DEVICE = "Google_Pixel_9_Pro_XL"


class SauceError(Exception):
    pass


class RealDeviceSession:
    """Thin client for the Real Device Access API, scoped to one device session."""

    def __init__(self, region, username, access_key):
        self.api = f"https://api.{region}.saucelabs.com"
        self.rda = f"{self.api}/rdc/v2"
        self.auth = (username, access_key)
        self.session_id = None

    def _request(self, method, url, expect=(200,), **kwargs):
        response = requests.request(method, url, auth=self.auth, timeout=120, **kwargs)
        if response.status_code not in expect:
            raise SauceError(
                f"{method} {url} returned {response.status_code}: {response.text[:500]}"
            )
        return response

    # --- entitlement probe -------------------------------------------------

    def device_catalog(self):
        """Returns the full device catalog, confirming the account can reach the API.

        Deliberately unfiltered: the endpoint's own deviceId filter gives no way to tell
        "this account has no devices" apart from "your pattern matched nothing", and the
        descriptors here are not the ids saucectl uses (no region suffix).
        """
        response = requests.get(f"{self.rda}/devices", auth=self.auth, timeout=60)
        if response.status_code in (401, 403):
            raise SauceError(
                "Real Device Access API rejected these credentials "
                f"({response.status_code}). It is an open-beta add-on, so the account "
                "most likely lacks entitlement -- ask Sauce to enable it."
            )
        if response.status_code != 200:
            raise SauceError(
                f"GET /devices returned {response.status_code}: {response.text[:500]}"
            )
        return response.json()

    # --- app storage -------------------------------------------------------

    def upload_app(self, apk):
        """Uploads an APK to App Storage and returns its `storage:<id>` reference."""
        with open(apk, "rb") as payload:
            response = self._request(
                "POST",
                f"{self.api}/v1/storage/upload",
                expect=(200, 201),
                files={"payload": (apk.name, payload)},
                data={"name": apk.name},
            )
        reference = "storage:" + response.json()["item"]["id"]
        print(f"Uploaded {apk.name} as {reference}")
        return reference

    # --- session lifecycle -------------------------------------------------

    def open(self, device_name, duration="PT1H"):
        # device_name is a concrete descriptor id resolved from the catalog, not a pattern,
        # so we do not depend on the server's regex semantics.
        body = {
            "device": {"deviceName": device_name, "os": "android"},
            "configuration": {"sessionDuration": duration},
        }
        self.session_id = self._request("POST", f"{self.rda}/sessions", json=body).json()["id"]
        print(f"Session {self.session_id} requested on {device_name}")
        self._await_state("ACTIVE")
        return self.session_id

    def _await_state(self, wanted, timeout=600):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            state = self._request("GET", f"{self.rda}/sessions/{self.session_id}").json()["state"]
            if state == wanted:
                return
            if state in ("CLOSED", "ERRORED"):
                raise SauceError(f"Session entered {state} while waiting for {wanted}")
            time.sleep(5)
        raise SauceError(f"Session did not reach {wanted} within {timeout}s")

    def close(self):
        if not self.session_id:
            return
        # Best effort: a leaked session holds the device until sessionDuration expires.
        try:
            self._request(
                "DELETE", f"{self.rda}/sessions/{self.session_id}", expect=(200, 204, 404)
            )
            print(f"Session {self.session_id} closed")
        except (SauceError, requests.RequestException) as error:
            print(f"WARNING: failed to close session {self.session_id}: {error}")

    # --- device interactions ----------------------------------------------

    def _device(self, endpoint, **kwargs):
        return self._request(
            "POST", f"{self.rda}/sessions/{self.session_id}/device/{endpoint}", **kwargs
        )

    def shell(self, command):
        return self._device("executeShellCommand", json={"adbShellCommand": command}).json()[
            "stdout"
        ]

    def install(self, app_reference, timeout=600):
        # enableInstrumentation=false keeps the APKs byte-identical: Sauce's
        # instrumentation re-signs and hooks the app, which would mean benchmarking
        # something other than what we built. We need none of the features it unlocks.
        started = self._device(
            "installApp", json={"app": app_reference, "enableInstrumentation": False}
        ).json()
        # Track by installationId rather than the app reference, which Sauce may echo back
        # in a normalised form.
        installation_id = started["installationId"]
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            installations = self._device("listAppInstallations").json()["appInstallations"]
            status = next(
                (i for i in installations if i.get("installationId") == installation_id), None
            )
            if status and status["status"] == "FINISHED":
                print(f"Installed {app_reference}")
                return
            if status and status["status"] == "ERROR":
                raise SauceError(f"Installation of {app_reference} failed")
            time.sleep(5)
        raise SauceError(f"Installation of {app_reference} did not finish within {timeout}s")

    def disable_animations(self):
        self._device("applySettings", json={"animations": False}, expect=(204,))

    def list_files(self, path):
        return self._device("listFiles", json={"path": path}).json()

    def pull_file(self, path):
        return self._device("pullFile", json={"path": path}).content


def instrumentation_args(skip_compilation):
    """Builds the `-e key value` arguments for `am instrument`.

    Deliberately not offered here:

    - androidx.benchmark.dryRunMode.enable would cut the run to one iteration, but it
      also forces outputEnable to false (Arguments.kt), so no benchmarkData.json is
      written at all -- a dry run cannot verify that retrieval works.
    - androidx.benchmark.iterations only feeds the *micro*benchmark path
      (BenchmarkStateLegacy, MicrobenchmarkPhase). Macrobenchmark takes its iteration
      count from the test source and ignores the argument, so skipping AOT compilation
      is the only way to shorten a run while still producing results.
    """
    if not skip_compilation:
        return ""
    return "-e androidx.benchmark.compilation.enabled false"


def run_benchmark(device, skip_compilation, timeout):
    """Starts the instrumentation detached and waits for it to finish."""
    # Leading rm is belt and braces -- Outputs also clears its dir on startup. Separated by
    # `;` so a missing dir on a fresh device does not stop the mkdir.
    device.shell(f"rm -rf {SHELL_SCRATCH_DIR} {DEVICE_OUTPUT_DIR}; mkdir -p {SHELL_SCRATCH_DIR}")

    stdout_file = f"{SHELL_SCRATCH_DIR}/instrumentation.txt"
    exit_code_file = f"{SHELL_SCRATCH_DIR}/exitcode"
    instrumentation = (
        f"am instrument -w -r {instrumentation_args(skip_compilation)} "
        f"{TEST_PACKAGE}/{TEST_RUNNER}"
    )
    # Detached, because executeShellCommand is documented to time out on long-running
    # commands (504 "Please do not execute long running adb commands") and a cold-start
    # benchmark runs for minutes. The exit code file is the completion signal.
    device.shell(
        f"nohup sh -c '{instrumentation} > {stdout_file} 2>&1; "
        f"echo $? > {exit_code_file}' > /dev/null 2>&1 &"
    )
    print(f"Started: {instrumentation}")

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if device.shell(f"cat {exit_code_file} 2>/dev/null").strip():
            break
        time.sleep(15)
    else:
        raise SauceError(f"Benchmark did not finish within {timeout}s")

    exit_code = device.shell(f"cat {exit_code_file}").strip()
    output = device.shell(f"cat {stdout_file}")
    print(f"--- instrumentation output (exit {exit_code}) ---\n{output}")
    return exit_code, output


def pull_results(device, out_dir):
    """Pulls the benchmark JSON and perfetto traces out of the device output dir."""
    out_dir.mkdir(parents=True, exist_ok=True)
    try:
        entries = device.list_files(DEVICE_OUTPUT_DIR)
    except SauceError as error:
        raise SauceError(
            f"Could not list {DEVICE_OUTPUT_DIR}: {error}. The benchmark likely never "
            "wrote results -- check the instrumentation output above."
        )

    pulled = []
    for entry in entries:
        name = entry.rsplit("/", 1)[-1]
        if not (name.endswith(".json") or name.endswith(".perfetto-trace")):
            continue
        path = entry if entry.startswith("/") else f"{DEVICE_OUTPUT_DIR}/{name}"
        target = out_dir / name
        target.write_bytes(device.pull_file(path))
        pulled.append(target)
        print(f"Pulled {path} -> {target}")

    if not pulled:
        raise SauceError(f"No results found in {DEVICE_OUTPUT_DIR} (saw: {entries})")
    return pulled


def android_devices(catalog):
    return [d for d in catalog if str(d.get("os", "")).upper() == "ANDROID"]


def matching_devices(catalog, pattern):
    """Android descriptors whose id or human-readable name matches `pattern`."""
    regex = re.compile(pattern, re.IGNORECASE)
    return [
        d
        for d in android_devices(catalog)
        if regex.search(d.get("id", "")) or regex.search(d.get("name", ""))
    ]


def describe_catalog(catalog, pattern):
    android = android_devices(catalog)
    lines = [
        f"Real Device Access API reachable: {len(catalog)} device(s) in the catalog, "
        f"{len(android)} Android."
    ]
    matches = matching_devices(catalog, pattern)
    lines.append(f"{len(matches)} match {pattern!r}: {[d['id'] for d in matches[:10]]}")
    if not matches:
        # Print the catalog so a naming mismatch is diagnosable from one run: RDA
        # descriptors have no region suffix, unlike the ids in .sauce/*.yml.
        lines.append("Available Android devices:")
        lines += [
            f"  {d.get('id')}  ({d.get('name')}, {d.get('os')} {d.get('osVersion')})"
            for d in sorted(android, key=lambda d: d.get("id", ""))
        ]
    return "\n".join(lines)


def print_summary(benchmark_data):
    data = json.loads(benchmark_data.read_text())
    context = data["context"]
    build = context["build"]
    print(
        f"\n{build['brand']} {build['model']} (api {build['version']['sdk']}), "
        f"compilation {context['compilationMode']}, cpuLocked={context['cpuLocked']}"
    )
    for benchmark in data["benchmarks"]:
        for metric, result in sorted(benchmark["metrics"].items()):
            print(
                f"  {benchmark['name']} {metric}: "
                f"min {result['minimum']:.1f} / median {result['median']:.1f} / "
                f"max {result['maximum']:.1f} "
                f"(CoV {result['coefficientOfVariation'] * 100:.1f}%, "
                f"{len(result['runs'])} iterations)"
            )


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--app", type=Path, help="target app APK (sentry-samples-android release)")
    parser.add_argument("--test-app", type=Path, help="macrobenchmark instrumentation APK")
    parser.add_argument("--device-name", default=DEFAULT_DEVICE, help="device id or regex")
    parser.add_argument("--region", default="us-west-1")
    parser.add_argument(
        "--skip-compilation",
        action="store_true",
        help="skip AOT compilation; much faster, but the numbers are not comparable",
    )
    parser.add_argument("--out-dir", type=Path, default=Path("artifacts/macrobenchmark"))
    parser.add_argument("--timeout", type=int, default=2400, help="seconds to wait for the run")
    parser.add_argument(
        "--probe-only",
        action="store_true",
        help="only check API entitlement and device availability, then exit",
    )
    args = parser.parse_args()

    username = os.environ.get("SAUCE_USERNAME")
    access_key = os.environ.get("SAUCE_ACCESS_KEY")
    if not username or not access_key:
        sys.exit("SAUCE_USERNAME and SAUCE_ACCESS_KEY must be set")

    device = RealDeviceSession(args.region, username, access_key)

    try:
        catalog = device.device_catalog()
    except SauceError as error:
        sys.exit(f"Real Device Access API probe failed: {error}")

    print(describe_catalog(catalog, args.device_name))
    matches = matching_devices(catalog, args.device_name)
    # Checked before --probe-only returns, so the probe fails loudly on a device name that
    # matches nothing rather than passing and letting the real run discover it.
    if not matches:
        sys.exit(f"No Android device matches {args.device_name!r}")
    device_id = matches[0]["id"]
    print(f"Using device {device_id}")
    if args.probe_only:
        return
    if not args.app or not args.test_app:
        sys.exit("--app and --test-app are required unless --probe-only is given")

    app_reference = device.upload_app(args.app)
    test_app_reference = device.upload_app(args.test_app)

    try:
        device.open(device_id)
        device.install(app_reference)
        device.install(test_app_reference)
        device.disable_animations()

        exit_code, _ = run_benchmark(device, args.skip_compilation, args.timeout)
        results = pull_results(device, args.out_dir)
    finally:
        device.close()

    for result in results:
        if result.name.endswith("-benchmarkData.json"):
            print_summary(result)

    if exit_code != "0":
        sys.exit(f"Instrumentation exited with {exit_code}")


if __name__ == "__main__":
    main()
