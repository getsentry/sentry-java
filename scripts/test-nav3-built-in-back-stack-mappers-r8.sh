#!/usr/bin/env bash
# Verifies BackStackEntryMapper.SerialName in R8-minified builds on a booted emulator or device.
# See sentry-android-integration-tests/nav3-built-in-back-stack-mappers-r8-check/README.md.
#
# Usage:
#   test-nav3-built-in-back-stack-mappers-r8.sh             Build all APKs, then run them.
#   test-nav3-built-in-back-stack-mappers-r8.sh build DIR   Build all APKs into DIR.
#   test-nav3-built-in-back-stack-mappers-r8.sh run DIR     Install and run every APK built into DIR.
set -euo pipefail

module="nav3-built-in-back-stack-mappers-r8-check"
moduleDir="sentry-android-integration-tests/nav3-built-in-back-stack-mappers-r8-check"
appId="io.sentry.tests.nav3.r8"
tag="NAV3_R8_CHECK"

copyApk() {
  cp "${moduleDir}/build/outputs/apk/release/nav3-built-in-back-stack-mappers-r8-check-release.apk" "$1"
}

# Prints the subdirectory of DIR $1 that holds this script's APKs.
apkDirIn() {
  echo "$1/${module}"
}

build() {
  local outDir
  outDir="$(apkDirIn "$1")"
  mkdir -p "$outDir"
  # Otherwise run() would also run APKs for versions no longer tested.
  rm -f "$outDir"/*.apk
  for mode in full compat; do
    local fullMode=true
    [[ "$mode" == "compat" ]] && fullMode=false
    echo "Building R8 $mode mode APK (kotlinx floor)..."
    ./gradlew -p sentry-android-integration-tests ":${module}:assembleRelease" "-Pandroid.enableR8.fullMode=${fullMode}"
    copyApk "${outDir}/${mode}.apk"

    # Compat mode keeps more than full mode, so it's only tested with the floor, where it changes
    # the outcome for named companion objects (kotlinx.serialization's own R8 rules only cover them
    # from 1.10.0 on).
    [[ "$mode" == "compat" ]] && continue

    echo "Building R8 $mode mode APK (kotlinx latestStable)..."
    ./gradlew -p sentry-android-integration-tests ":${module}:assembleRelease" \
      "-Pandroid.enableR8.fullMode=${fullMode}" "-Pnav3CheckKotlinx=latestStable"
    copyApk "${outDir}/${mode}-kotlinx-latestStable.apk"
  done
}

# Installs and launches APK $1, prints its check's output, and fails unless the check passed.
runApk() {
  local apk="$1"
  adb uninstall "$appId" > /dev/null 2>&1 || true
  adb install "$apk" > /dev/null || return 1
  # Reads only this launch's lines, so the previous APK's RESULT=PASS can't hide a failure. The
  # timestamp has second precision, hence the sleep.
  local since
  sleep 1
  since="$(adb shell "date +'%m-%d %H:%M:%S.000'" | tr -d '\r')" || return 1
  adb shell am start -W -n "${appId}/.BuiltInBackStackMappersActivity" > /dev/null || return 1

  local output=""
  for _ in $(seq 1 60); do
    output="$(adb logcat -d -T "$since" -s "${tag}:I" | grep "${tag}" || true)"
    if grep -q "RESULT=" <<< "$output"; then
      break
    fi
    if adb logcat -d -T "$since" -b crash | grep -q "$appId"; then
      break
    fi
    sleep 1
  done

  if [[ -n "$output" ]]; then
    sed -E "s/^.*${tag}: //" <<< "$output"
  fi
  if ! grep -q "RESULT=PASS" <<< "$output"; then
    adb logcat -d -T "$since" -b crash | grep -A 20 "$appId" || true
    return 1
  fi
}

run() {
  local apkDir
  apkDir="$(apkDirIn "$1")"
  local failed=0
  shopt -s nullglob
  local apks=("$apkDir"/*.apk)
  if [[ ${#apks[@]} -eq 0 ]]; then
    echo "No APKs found in $apkDir"
    exit 1
  fi

  for apk in "${apks[@]}"; do
    local name
    name="$(basename "$apk" .apk)"
    echo "=== $name"
    if ! runApk "$apk"; then
      echo "FAILED: $name"
      failed=1
    fi
  done

  if [[ $failed -ne 0 ]]; then
    echo "Nav3 Built-in Back Stack Mappers R8 check failed."
    exit 1
  fi
  echo "Nav3 Built-in Back Stack Mappers R8 check passed."
}

case "${1:-}" in
  build) build "${2:?Usage: $0 build DIR}" ;;
  run) run "${2:?Usage: $0 run DIR}" ;;
  "")
    apkDir="${moduleDir}/build/r8-check-apks"
    build "$apkDir"
    run "$apkDir"
    ;;
  *)
    echo "Usage: $0 [build DIR | run DIR]"
    exit 1
    ;;
esac
