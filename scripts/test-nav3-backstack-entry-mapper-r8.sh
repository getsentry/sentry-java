#!/usr/bin/env bash
# Verifies the behavior of BackStackEntryMapper.SerialName and SerialNameAndProperties
# in R8-minified builds on a booted emulator or device. See
# sentry-android-integration-tests/nav3-backstack-entry-mapper-r8-check/README.md.
#
# The app is built against the kotlinxSerializationFloor in gradle/nav3.versions.toml
# plus each version in NAV3_EXTRA_KOTLINX_VERSIONS (default: defaultLatestKotlinx below),
# each in R8 full and compat modes. Also builds nav3-no-kotlinx-r8-check once, which checks that an
# app without kotlinx.serialization builds and runs.
#
# Usage:
#   test-nav3-backstack-entry-mapper-r8.sh             Build all APKs, then run them.
#   test-nav3-backstack-entry-mapper-r8.sh build DIR   Build all APKs into DIR.
#   test-nav3-backstack-entry-mapper-r8.sh run DIR     Install and run every APK in DIR.
set -euo pipefail

module="nav3-backstack-entry-mapper-r8-check"
moduleDir="sentry-android-integration-tests/nav3-backstack-entry-mapper-r8-check"
appId="io.sentry.tests.nav3.r8"
tag="NAV3_R8_CHECK"
noKotlinxModule="nav3-no-kotlinx-r8-check"
noKotlinxModuleDir="sentry-android-integration-tests/nav3-no-kotlinx-r8-check"

# Newer kotlinx.serialization versions to also build the app against, on top of the
# kotlinxSerializationFloor. Lets us prevent regressions should host apps ship a newer
# kotlinx.serialization version than we compile against. Also protects us against changes
# to the experimental kotlinx.serialization APIs used by the arguments encoder. Override
# with, e.g., NAV3_EXTRA_KOTLINX_VERSIONS="1.10.0 1.12.0" to test the compile floor,
# 1.10.0, and 1.12.0. (Provide an empty string to test the floor only.)
#
# The latest kotlinx-serialization-core release. Bumped by a weekly PR from
# scripts/update-nav3-kotlinx-serialization.sh (.github/workflows/update-deps.yml).
defaultLatestKotlinx="1.13.0"
extraKotlinxVersions=(${NAV3_EXTRA_KOTLINX_VERSIONS-$defaultLatestKotlinx})

copyApk() {
  cp "${moduleDir}/build/outputs/apk/release/nav3-backstack-entry-mapper-r8-check-release.apk" "$1"
}

build() {
  local outDir="$1"
  mkdir -p "$outDir"
  for mode in full compat; do
    local fullMode=true
    [[ "$mode" == "compat" ]] && fullMode=false
    echo "Building R8 $mode mode APK (kotlinx floor)..."
    ./gradlew -p sentry-android-integration-tests ":${module}:assembleRelease" "-Pandroid.enableR8.fullMode=${fullMode}"
    copyApk "${outDir}/${mode}.apk"

    # Apps may ship a newer kotlinx than our compile floor, so rebuild against each.
    for version in ${extraKotlinxVersions[@]+"${extraKotlinxVersions[@]}"}; do
      echo "Building R8 $mode mode APK (kotlinx ${version})..."
      ./gradlew -p sentry-android-integration-tests ":${module}:assembleRelease" \
        "-Pandroid.enableR8.fullMode=${fullMode}" "-Pnav3CheckKotlinxVersion=${version}"
      copyApk "${outDir}/${mode}-kotlinx-${version}.apk"
    done
  done

  # Shares the R8 check's application ID, Activity, and log tag, so run() handles its APK the same
  # way. R8 full mode only, as the check is whether R8 accepts the missing classes.
  echo "Building R8 full mode APK without kotlinx.serialization..."
  ./gradlew -p sentry-android-integration-tests ":${noKotlinxModule}:assembleRelease"
  cp "${noKotlinxModuleDir}/build/outputs/apk/release/${noKotlinxModule}-release.apk" "${outDir}/no-kotlinx.apk"
}

run() {
  local apkDir="$1"
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
    adb uninstall "$appId" > /dev/null 2>&1 || true
    adb install -r -d "$apk" > /dev/null
    # Only read lines logged from this launch on, so a stale RESULT=PASS from the previous APK can't
    # hide this one's failure. Doesn't clear the log, which would also discard unrelated logs on a
    # development device. The timestamp is truncated to the second, so wait a second for it to
    # postdate earlier lines.
    local since
    sleep 1
    since="$(adb shell "date +'%m-%d %H:%M:%S.000'" | tr -d '\r')"
    adb shell am start -W -n "${appId}/.BackStackEntryMapperActivity" > /dev/null

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
      echo "FAILED: $name"
      adb logcat -d -T "$since" -b crash | grep -A 20 "$appId" || true
      failed=1
    fi
  done

  if [[ $failed -ne 0 ]]; then
    echo "Nav3 BackStackEntryMapper R8 check failed."
    exit 1
  fi
  echo "Nav3 BackStackEntryMapper R8 check passed."
}

case "${1:-}" in
  build) build "${2:?Usage: $0 build DIR}" ;;
  run) run "${2:?Usage: $0 run DIR}" ;;
  "")
    apkDir="${moduleDir}/build/r8-check-apks"
    rm -rf "$apkDir"
    build "$apkDir"
    run "$apkDir"
    ;;
  *)
    echo "Usage: $0 [build DIR | run DIR]"
    exit 1
    ;;
esac
