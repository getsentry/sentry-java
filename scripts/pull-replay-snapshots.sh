#!/bin/sh
# Pulls the replay frame snapshots that ReplaySnapshotTest writes on the connected device.
# Keep the device directory in sync with ReplaySnapshotTest.
set -eu

out_dir=$1
device_dir=/sdcard/Download/sentry_replay_snapshots

mkdir -p "$out_dir"
if adb shell test -d "$device_dir"; then
  adb pull "$device_dir/." "$out_dir"
else
  printf 'No replay snapshots on the device, skipping\n'
fi
