#!/usr/bin/env bash
# Checks BackStackEntryMapper.SerialNames and SerialNamesAndArguments in R8-minified builds on a
# booted emulator or device.
# See sentry-android-integration-tests/nav3-serialname-r8-check/README.md.
#
# The withSerialization flavor is built against the kotlinx.serialization compile floor (the pinned
# libs version) and each version in NAV3_EXTRA_KOTLINX_VERSIONS (default: defaultLatestKotlinx
# below); withoutSerialization is built without kotlinx. Each is built in R8 full and compat mode.
#
# Usage:
#   test-nav3-serialname-r8.sh             Build all APKs, then run them.
#   test-nav3-serialname-r8.sh build DIR   Build all APKs into DIR.
#   test-nav3-serialname-r8.sh run DIR     Install and run every APK in DIR.
set -euo pipefail

module="nav3-serialname-r8-check"
moduleDir="sentry-android-integration-tests/nav3-serialname-r8-check"
appId="io.sentry.tests.nav3serialname"
tag="NAV3_R8_CHECK"

# Newer kotlinx.serialization versions to also build the withSerialization flavor against, on top of
# the compile floor (the pinned libs version). Apps may ship a newer kotlinx than we compile against,
# and the arguments encoder uses experimental API, so the check runs the floor plus these. Override
# with NAV3_EXTRA_KOTLINX_VERSIONS="1.9.0 1.12.0" (empty string to test the floor only).
#
# The latest kotlinx.serialization release. Bumped by a daily PR from
# scripts/update-nav3-kotlinx-serialization.sh (.github/workflows/update-deps.yml); keep it on one line.
defaultLatestKotlinx="1.12.0"
extraKotlinxVersions=(${NAV3_EXTRA_KOTLINX_VERSIONS-$defaultLatestKotlinx})

copyApk() {
  local flavor="$1" dest="$2"
  cp "${moduleDir}/build/outputs/apk/${flavor}/release/nav3-serialname-r8-check-${flavor}-release.apk" \
    "$dest"
}

build() {
  local outDir="$1"
  mkdir -p "$outDir"
  for mode in full compat; do
    local fullMode=true
    [[ "$mode" == "compat" ]] && fullMode=false
    echo "Building R8 $mode mode APKs (kotlinx floor)..."
    ./gradlew -p sentry-android-integration-tests ":${module}:assembleRelease" "-Pandroid.enableR8.fullMode=${fullMode}"
    copyApk withSerialization "${outDir}/${mode}-withSerialization.apk"
    copyApk withoutSerialization "${outDir}/${mode}-withoutSerialization.apk"

    # Apps may ship a newer kotlinx than our compile floor, so rebuild withSerialization against each.
    for version in ${extraKotlinxVersions[@]+"${extraKotlinxVersions[@]}"}; do
      echo "Building R8 $mode mode withSerialization APK (kotlinx ${version})..."
      ./gradlew -p sentry-android-integration-tests ":${module}:assembleWithSerializationRelease" \
        "-Pandroid.enableR8.fullMode=${fullMode}" "-Pnav3CheckKotlinxVersion=${version}"
      copyApk withSerialization "${outDir}/${mode}-withSerialization-kotlinx-${version}.apk"
    done
  done
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
    adb logcat -c
    adb shell am start -W -n "${appId}/.CheckActivity" > /dev/null

    local output=""
    for _ in $(seq 1 60); do
      output="$(adb logcat -d -s "${tag}:I" | grep "${tag}" || true)"
      if grep -q "RESULT=" <<< "$output"; then
        break
      fi
      if adb logcat -d -b crash | grep -q "$appId"; then
        break
      fi
      sleep 1
    done

    if [[ -n "$output" ]]; then
      sed -E "s/^.*${tag}: //" <<< "$output"
    fi
    if ! grep -q "RESULT=PASS" <<< "$output"; then
      echo "FAILED: $name"
      adb logcat -d -b crash | grep -A 20 "$appId" || true
      failed=1
    fi
  done

  if [[ $failed -ne 0 ]]; then
    echo "Nav3 serialName R8 check failed."
    exit 1
  fi
  echo "Nav3 serialName R8 check passed."
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
