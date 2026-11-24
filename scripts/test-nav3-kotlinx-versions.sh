#!/usr/bin/env bash
# Runs all of sentry-android-navigation3's unit tests against newer kotlinx.serialization versions
# than the floor they compile against: kotlinxSerialization-stable in
# gradle/dependency-matrix.versions.toml and, if it's newer, kotlinxSerialization-preview (or each
# version in NAV3_EXTRA_KOTLINX_VERSIONS instead). The Build workflow runs them against the floor.
#
# Runs the whole suite rather than only the tests that appear to use kotlinx.serialization, as a
# test can depend on it through a shared fixture without naming it in its own source.
#
# A release candidate's failures are advisory: the script warns instead of failing, unless
# NAV3_PREVIEW_REQUIRED=true (set by CI on PRs that change kotlinxSerialization-preview).
#
# Usage: test-nav3-kotlinx-versions.sh
set -euo pipefail

cd "$(dirname "$0")/../"
previewRequired="${NAV3_PREVIEW_REQUIRED:-false}"

# Reports a problem as a GitHub Actions annotation in CI, and as a plain warning otherwise.
warn() {
  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    echo "::warning::$1"
  else
    echo "WARNING: $1"
  fi
}

# "<role> <version>" lines, as for scripts/test-nav3-backstack-entry-mapper-r8.sh.
if [[ -n "${NAV3_EXTRA_KOTLINX_VERSIONS+x}" ]]; then
  versions=""
  for version in $NAV3_EXTRA_KOTLINX_VERSIONS; do
    versions+="extra $version"$'\n'
  done
else
  versions="$(scripts/dependency-matrix.sh extra-versions kotlinxSerialization)"
fi

failed=0
while read -r role version; do
  [[ -z "$role" ]] && continue
  echo "Running unit tests against kotlinx.serialization $version ($role)..."
  # Reads no stdin, so it can't consume the loop's remaining versions.
  if ./gradlew :sentry-android-navigation3:testReleaseUnitTest \
    -Pnav3TestKotlinxVersion="$version" < /dev/null; then
    :
  elif [[ "$role" == "preview" && "$previewRequired" != "true" ]]; then
    warn "Unit tests failed against kotlinx.serialization release candidate $version."
  else
    failed=1
  fi
done <<< "$versions"

if [[ $failed -ne 0 ]]; then
  echo "sentry-android-navigation3 unit tests failed against a newer kotlinx.serialization version."
  exit 1
fi
