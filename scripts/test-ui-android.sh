#!/bin/sh
# Runs the sentry-uitest-android instrumentation tests on the connected device, then pulls the
# replay frame snapshots that ReplaySnapshotTest writes, also when the tests fail.
# android-emulator-runner runs each `script:` line in its own shell, so this stays one script to
# keep the tests' exit code. Keep the device directory in sync with ReplaySnapshotTest.
set -eu

out_dir=$1
device_dir=/sdcard/Download/sentry_replay_snapshots

test_status=0
./gradlew sentry-android-integration-tests:sentry-uitest-android:connectedReleaseAndroidTest || test_status=$?

mkdir -p "$out_dir"
if adb shell test -d "$device_dir"; then
  adb pull "$device_dir/." "$out_dir"
else
  printf 'No replay snapshots on the device, skipping\n'
fi

exit "$test_status"
