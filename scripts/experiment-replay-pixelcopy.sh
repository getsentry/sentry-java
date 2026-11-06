#!/bin/sh
# EXPERIMENT (do not merge): run ReplaySnapshotTest on a GH Actions emulator and collect
# the captured replay frames and logcat, to check whether PixelCopy works there.
set -eu

out_dir=$1
mkdir -p "$out_dir"

rc=0
./gradlew :sentry-android-integration-tests:sentry-uitest-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.sentry.uitest.android.ReplaySnapshotTest \
  -Denvironment=github || rc=$?

adb pull /sdcard/Download/sauce_labs_custom_screenshots "$out_dir/snapshots" ||
  printf 'No replay snapshots to pull\n'
adb logcat -d >"$out_dir/logcat.txt" || printf 'Could not dump logcat\n'

exit "$rc"
