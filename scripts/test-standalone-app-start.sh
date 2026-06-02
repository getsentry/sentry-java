#!/usr/bin/env bash
# End-to-end test harness for standalone app start tracing (issue #5046).
# Exercises scenarios 1a, 1c, 2a-2f against two running emulators.
# Requires: adb, two running emulators (API 36 + API 33), JDK 17.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home}"

PKG="io.sentry.samples.android"
APK_SRC="sentry-samples/sentry-samples-android/build/outputs/apk/debug/sentry-samples-android-debug.apk"
EMU_API36="emulator-5554"
EMU_API33="emulator-5556"
OUT_DIR="/tmp/standalone-app-start-logs"
APK_A="$OUT_DIR/APK-A.apk"  # flag=on,  simulate-plugin=on   (tier 1)
APK_B="$OUT_DIR/APK-B.apk"  # flag=off, simulate-plugin=off  (regression)
APK_C="$OUT_DIR/APK-C.apk"  # flag=on,  simulate-plugin=off  (tier 2 / tier 3)
MAIN_ACTIVITY="${PKG}/.MainActivity"
RECEIVER="${PKG}/.TestBroadcastReceiver"
BROADCAST_ACTION="io.sentry.samples.android.TEST_BROADCAST"
SERVICE="${PKG}/.DummyService"

# Wait durations (seconds). Must exceed the transaction deadline timeout (30s default)
# because the sample MainActivity keeps the main thread busy so idleTimeout doesn't fire.
WAIT_ACTIVITY=35
WAIT_BROADCAST=8
WAIT_COMBO=40
# Max time we wait for Sentry to actually flush cached envelopes to sentry.io after we
# foreground the app. Emulators often fail on IPv6 first and take ~60-75s before falling
# back to IPv4 on the transport's retry, so 120s gives comfortable slack.
DELIVERY_TIMEOUT=120

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*.log

PASS=0
FAIL=0
FAIL_LINES=()

red()   { printf "\033[31m%s\033[0m" "$*"; }
green() { printf "\033[32m%s\033[0m" "$*"; }
bold()  { printf "\033[1m%s\033[0m" "$*"; }

banner() { echo ""; bold "=========  $*  ========="; echo ""; }

build_apk() {
  local flag=$1 simulate=$2 dest=$3 label=$4
  echo "--- build $label: -PstandaloneAppStart=$flag -PsimulateSentryGradlePlugin=$simulate ---"
  ./gradlew :sentry-samples:sentry-samples-android:assembleDebug \
    -PstandaloneAppStart="$flag" \
    -PsimulateSentryGradlePlugin="$simulate" \
    -q 2>&1 | tail -3
  if [[ ! -f "$APK_SRC" ]]; then
    echo "BUILD FAILED: APK not produced"
    exit 1
  fi
  cp "$APK_SRC" "$dest"
  echo "    saved -> $dest"
}

install_from() {
  local device=$1 apk=$2
  echo "--- install $(basename $apk) on $device ---"
  adb -s "$device" install -r -t "$apk" 2>&1 | tail -1
}

wake_device() {
  local device=$1
  adb -s "$device" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb -s "$device" shell input keyevent 82 >/dev/null 2>&1 || true
}

prep_for_run() {
  local device=$1
  adb -s "$device" shell am force-stop "$PKG"
  adb -s "$device" logcat -c
  # brief pause to let system settle
  sleep 0.3
}

dump_log() {
  local device=$1 file=$2
  adb -s "$device" logcat -d -v time SentryE2E:D '*:S' > "$file"
  # Also capture full Sentry SDK debug log for diagnostics.
  adb -s "$device" logcat -d -v time Sentry:D SentryE2E:D StrictMode:D AndroidRuntime:E '*:S' \
    > "${file%.log}.full.log"
}

# Extract traceIds of logged transactions in order.
extract_traceids() {
  local file=$1
  grep -oE 'traceId=[a-f0-9]+' "$file" | sed 's/traceId=//'
}

# Extract unique names of logged transactions in order.
extract_names() {
  local file=$1
  awk '/SentryE2E.*TXN\|/ {
    match($0, /name=[^|]+/); n = substr($0, RSTART+5, RLENGTH-5); print n
  }' "$file"
}

assert() {
  local desc=$1 cmd=$2
  if eval "$cmd"; then
    echo "    $(green PASS) $desc"
    PASS=$((PASS+1))
  else
    echo "    $(red FAIL) $desc"
    FAIL=$((FAIL+1))
    FAIL_LINES+=("$desc")
  fi
}

# Post-scenario drain: foreground the app (via MainActivity) so AndroidConnectionStatusProvider
# flips from DISCONNECTED → CONNECTED (it reports null during broadcast-only cold starts),
# then poll logcat until at least one "Envelope sent successfully" appears. Safe to call after
# TXN assertions are done; the extra MainActivity launch emits its own ui.load but by then the
# scenario log was already captured.
verify_delivery() {
  local device=$1 scenario=$2
  adb -s "$device" shell am start -n "$MAIN_ACTIVITY" >/dev/null
  local drain_log="$OUT_DIR/${scenario}.delivery.log"
  local deadline=$((SECONDS + DELIVERY_TIMEOUT))
  local sent=0
  echo "    polling for envelope delivery (timeout ${DELIVERY_TIMEOUT}s)..."
  while (( SECONDS < deadline )); do
    adb -s "$device" logcat -d -v time Sentry:D '*:S' > "$drain_log"
    sent=$(grep -c 'Envelope sent successfully' "$drain_log" || true)
    [[ "$sent" -gt 0 ]] && break
    sleep 3
  done
  local queued
  queued=$(grep -c 'Adding Envelope to offline storage' "$drain_log" || true)
  assert "delivery: envelopes sent to Sentry (sent=$sent queued=$queued)" \
    "[[ '$sent' -gt '0' ]]"
}

# ================= SCENARIOS =================

build_needed_variants() {
  banner "Build variants"
  [[ $needs_A -eq 1 ]] && build_apk true  true  "$APK_A" "APK-A (flag=on,  simulate-plugin=on)"
  [[ $needs_B -eq 1 ]] && build_apk false false "$APK_B" "APK-B (flag=off, simulate-plugin=off)"
  [[ $needs_C -eq 1 ]] && build_apk true  false "$APK_C" "APK-C (flag=on,  simulate-plugin=off)"
}

scenario_1a() {
  banner "1a: Cold + flag ON (launcher) — API 36 / APK-A"
  install_from "$EMU_API36" "$APK_A"
  wake_device "$EMU_API36"
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am start -n "$MAIN_ACTIVITY" >/dev/null
  sleep $WAIT_ACTIVITY
  local log="$OUT_DIR/1a.log"
  dump_log "$EMU_API36" "$log"

  assert "App Start standalone txn emitted" \
    "grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  assert "MainActivity ui.load txn emitted" \
    "grep -qE 'name=MainActivity.*op=ui\.load' '$log'"
  assert "ui.load txn does NOT contain app.start.* child span" \
    "! grep -E 'name=MainActivity.*op=ui\.load' '$log' | grep -qE 'children=.*app\.start\.(cold|warm)'"
  assert "standalone txn has process.load or application.load child" \
    "grep -E '\|op=app\.start\|' '$log' | grep -qE 'children=.*(process\.load|application\.load)'"
  # Both txns share same traceId
  local tids=$(extract_traceids "$log" | sort -u)
  local count=$(echo "$tids" | wc -l | tr -d ' ')
  assert "standalone + ui.load share traceId (unique traceId count = 1)" \
    "[[ '$count' == '1' ]]"
  verify_delivery "$EMU_API36" "1a"
}

scenario_1c() {
  banner "1c: Cold + flag OFF (regression) — API 36 / APK-B"
  install_from "$EMU_API36" "$APK_B"
  wake_device "$EMU_API36"
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am start -n "$MAIN_ACTIVITY" >/dev/null
  sleep $WAIT_ACTIVITY
  local log="$OUT_DIR/1c.log"
  dump_log "$EMU_API36" "$log"

  assert "MainActivity ui.load txn emitted" \
    "grep -qE 'name=MainActivity.*op=ui\.load' '$log'"
  assert "ui.load txn CONTAINS app.start.* child span (legacy)" \
    "grep -E 'name=MainActivity.*op=ui\.load' '$log' | grep -qE 'children=.*app\.start\.(cold|warm)'"
  assert "NO standalone App Start txn emitted" \
    "! grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  verify_delivery "$EMU_API36" "1c"
}

scenario_2a() {
  banner "2a: Broadcast cold, tier 1 (simulated plugin) — API 36 / APK-A"
  install_from "$EMU_API36" "$APK_A"
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am broadcast -a "$BROADCAST_ACTION" -n "$RECEIVER" >/dev/null
  sleep $WAIT_BROADCAST
  local log="$OUT_DIR/2a.log"
  dump_log "$EMU_API36" "$log"

  assert "App Start standalone txn emitted" \
    "grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  assert "standalone has process.load child" \
    "grep -qE '\|op=app\.start\|.*children=.*process\.load' '$log'"
  assert "standalone has application.load child" \
    "grep -qE '\|op=app\.start\|.*children=.*application\.load' '$log'"
  assert "NO ui.load txn emitted" \
    "! grep -qE 'op=ui\.load' '$log'"
  verify_delivery "$EMU_API36" "2a"
}

scenario_2b() {
  banner "2b: Broadcast cold, tier 2 (ApplicationStartInfo) — API 36 / APK-C"
  install_from "$EMU_API36" "$APK_C"
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am broadcast -a "$BROADCAST_ACTION" -n "$RECEIVER" >/dev/null
  sleep $WAIT_BROADCAST
  local log="$OUT_DIR/2b.log"
  dump_log "$EMU_API36" "$log"

  assert "App Start standalone txn emitted (tier 2 API 35+)" \
    "grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  assert "standalone has process.load child" \
    "grep -qE '\|op=app\.start\|.*children=.*process\.load' '$log'"
  assert "NO ui.load txn emitted" \
    "! grep -qE 'op=ui\.load' '$log'"
  verify_delivery "$EMU_API36" "2b"
}

scenario_2c() {
  banner "2c: Broadcast cold, tier 3 (CLASS_LOADED fallback) — API 33 / APK-C"
  install_from "$EMU_API33" "$APK_C"
  prep_for_run "$EMU_API33"
  adb -s "$EMU_API33" shell am broadcast -a "$BROADCAST_ACTION" -n "$RECEIVER" >/dev/null
  sleep $WAIT_BROADCAST
  local log="$OUT_DIR/2c.log"
  dump_log "$EMU_API33" "$log"

  assert "App Start standalone txn emitted (tier 3 fallback)" \
    "grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  assert "standalone has process.load child" \
    "grep -qE '\|op=app\.start\|.*children=.*process\.load' '$log'"
  assert "NO ui.load txn emitted" \
    "! grep -qE 'op=ui\.load' '$log'"
  verify_delivery "$EMU_API33" "2c"
}

scenario_2d() {
  banner "2d: Foreground service cold start — API 36 / APK-A"
  install_from "$EMU_API36" "$APK_A"
  # Notification permission is needed for the foreground service notification
  adb -s "$EMU_API36" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am start-foreground-service -n "$SERVICE" >/dev/null
  sleep $WAIT_BROADCAST
  local log="$OUT_DIR/2d.log"
  dump_log "$EMU_API36" "$log"

  assert "App Start standalone txn emitted via foreground service" \
    "grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  assert "NO ui.load txn emitted" \
    "! grep -qE 'op=ui\.load' '$log'"
  verify_delivery "$EMU_API36" "2d"
}

scenario_2e() {
  banner "2e: Broadcast → launcher (trace reuse) — API 36 / APK-A"
  install_from "$EMU_API36" "$APK_A"
  wake_device "$EMU_API36"
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am broadcast -a "$BROADCAST_ACTION" -n "$RECEIVER" >/dev/null
  sleep 3
  adb -s "$EMU_API36" shell am start -n "$MAIN_ACTIVITY" >/dev/null
  sleep $WAIT_COMBO
  local log="$OUT_DIR/2e.log"
  dump_log "$EMU_API36" "$log"

  assert "App Start standalone txn emitted (from broadcast)" \
    "grep -qE 'name=App Start\|op=app\.start\|' '$log'"
  assert "MainActivity ui.load txn emitted" \
    "grep -qE 'name=MainActivity.*op=ui\.load' '$log'"
  assert "ui.load has NO app.start.* child" \
    "! grep -E 'name=MainActivity.*op=ui\.load' '$log' | grep -qE 'children=.*app\.start\.'"
  local tids=$(extract_traceids "$log" | sort -u)
  local count=$(echo "$tids" | wc -l | tr -d ' ')
  assert "broadcast + launcher txns share same traceId" \
    "[[ '$count' == '1' ]]"
  verify_delivery "$EMU_API36" "2e"
}

scenario_2f() {
  banner "2f: Broadcast + flag OFF (regression) — API 36 / APK-B"
  install_from "$EMU_API36" "$APK_B"
  prep_for_run "$EMU_API36"
  adb -s "$EMU_API36" shell am broadcast -a "$BROADCAST_ACTION" -n "$RECEIVER" >/dev/null
  sleep $WAIT_BROADCAST
  local log="$OUT_DIR/2f.log"
  dump_log "$EMU_API36" "$log"

  assert "NO transactions emitted (flag off + no activity)" \
    "! grep -qE 'SentryE2E.*TXN\\|' '$log'"
}

usage() {
  cat <<EOF
Usage: $0 [scenario ...]

Without args, runs all 8 scenarios. With args, runs only those named.
Scenarios: 1a 1c 2a 2b 2c 2d 2e 2f

Variant needed per scenario:
  1a,2a,2d,2e → APK-A      (flag=on,  simulate-plugin=on)
  1c,2f       → APK-B      (flag=off, simulate-plugin=off)
  2b,2c       → APK-C      (flag=on,  simulate-plugin=off)

Examples:
  $0              # all scenarios
  $0 2c           # just the tier-3 fallback case
  $0 1a 2e        # happy path + broadcast-then-launcher
EOF
}

# Determine which variants are needed based on requested scenarios.
needs_A=0; needs_B=0; needs_C=0
declare -a requested=()
if [[ $# -eq 0 ]]; then
  requested=(1a 1c 2a 2b 2c 2d 2e 2f)
  needs_A=1; needs_B=1; needs_C=1
else
  for s in "$@"; do
    case "$s" in
      -h|--help) usage; exit 0 ;;
      1a|2a|2d|2e) needs_A=1; requested+=("$s") ;;
      1c|2f)       needs_B=1; requested+=("$s") ;;
      2b|2c)       needs_C=1; requested+=("$s") ;;
      *) echo "unknown scenario: $s"; usage; exit 2 ;;
    esac
  done
fi

# ================ RUN =================

banner "Pre-flight"
adb devices -l
echo ""
adb -s "$EMU_API36" shell getprop ro.build.version.sdk | awk '{print "emulator-5554 API: "$0}'
adb -s "$EMU_API33" shell getprop ro.build.version.sdk | awk '{print "emulator-5556 API: "$0}'

build_needed_variants
for s in "${requested[@]}"; do
  "scenario_$s"
done

banner "SUMMARY"
echo "  PASS: $(green $PASS)"
echo "  FAIL: $(red $FAIL)"
if [[ $FAIL -gt 0 ]]; then
  echo ""
  echo "  Failing assertions:"
  for l in "${FAIL_LINES[@]}"; do
    echo "    - $l"
  done
fi
echo ""
echo "  Logs: $OUT_DIR"
exit $FAIL
