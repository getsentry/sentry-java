#!/usr/bin/env bash
# Runs sentry-android-navigation3's unit tests that use kotlinx.serialization against newer
# kotlinx.serialization versions than the floor they compile against: kotlinxSerialization-stable
# in gradle/dependency-matrix.versions.toml and, if it's newer, kotlinxSerialization-preview (or each
# version in NAV3_EXTRA_KOTLINX_VERSIONS instead). The Build workflow runs all the module's unit
# tests against the floor.
#
# Only runs test classes whose sources import kotlinx.serialization or the module's kotlinx package,
# or use the built-in mappers, as the module's other tests don't depend on the version.
#
# A release candidate's failures are advisory: the script warns instead of failing, unless
# NAV3_PREVIEW_REQUIRED=true (set by CI on PRs that change kotlinxSerialization-preview).
#
# Usage: test-nav3-kotlinx-versions.sh
set -euo pipefail

cd "$(dirname "$0")/../"
testSourceDir="sentry-android-navigation3/src/test/kotlin"
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

testFilters=()
while read -r file; do
  testClass="${file#"$testSourceDir"/}"
  testClass="${testClass%.kt}"
  testFilters+=(--tests "${testClass//\//.}")
done < <(
  grep -rlE \
    '^import (kotlinx\.serialization|io\.sentry\.compose\.navigation3\.internal\.backstack\.kotlinx)|BuiltInBackStackEntryMappers|BackStackEntryMapper\.SerialName' \
    "$testSourceDir" --include='*Test.kt' | sort
)
if [[ ${#testFilters[@]} -eq 0 ]]; then
  echo "Found no unit tests that use kotlinx.serialization in $testSourceDir"
  exit 1
fi

failed=0
while read -r role version; do
  [[ -z "$role" ]] && continue
  echo "Running unit tests against kotlinx.serialization $version ($role)..."
  # Reads no stdin, so it can't consume the loop's remaining versions.
  if ./gradlew :sentry-android-navigation3:testReleaseUnitTest \
    -Pnav3TestKotlinxVersion="$version" "${testFilters[@]}" < /dev/null; then
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
