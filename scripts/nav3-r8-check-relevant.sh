#!/usr/bin/env bash
# Prints "true" if the changes between two commits can affect the Nav3 R8 check
# (.github/workflows/integration-tests-nav3-r8.yml), and "false" otherwise.
#
# The workflow runs on every pull request and every push to main, and this script is the only
# place that decides which changes the check needs to run for. (The workflow deliberately has no
# paths filters, so there's no second list to keep in sync with RELEVANT_PATHS below.)
#
# A change is relevant if it touches any of RELEVANT_PATHS, or changes a relevant version in one of
# the two version catalogs:
#
# - gradle/libs.versions.toml: bumps of the toolchain and libraries the check depends on (Kotlin and
#   its serialization plugin, AGP and its R8, Compose, lifecycle, savedstate, Nav3) can change how
#   our mappers behave under R8. Most of that file's changes bump unrelated dependencies, though,
#   so for those the check is skipped.
# - gradle/dependency-matrix.versions.toml: holds the kotlinx.serialization versions the check runs
#   against, and may hold other matrices' versions. Only changes to lines that name the
#   kotlinxSerialization matrix are relevant.
#
# With --any-catalog-change, any change to either catalog is relevant. Pushes to main use it, so a
# relevant version that matches none of the patterns below is still checked once it's merged.
#
# Usage: nav3-r8-check-relevant.sh [--any-catalog-change] BASE HEAD
set -euo pipefail

anyCatalogChange=false
if [[ "${1:-}" == "--any-catalog-change" ]]; then
  anyCatalogChange=true
  shift
fi
base="${1:?Usage: $0 [--any-catalog-change] BASE HEAD}"
head="${2:?Usage: $0 [--any-catalog-change] BASE HEAD}"
versionCatalog="gradle/libs.versions.toml"
matrixCatalog="gradle/dependency-matrix.versions.toml"

# Paths (extended regular expressions, matched against the whole path) whose changes are always
# relevant.
RELEVANT_PATHS=(
  'sentry-android-navigation3/.*'
  'sentry-android-integration-tests/nav3-backstack-entry-mapper-r8-check/.*'
  # The settings, build script, and properties of the build that produces the R8 check's APKs.
  'sentry-android-integration-tests/[^/]*\.(gradle\.kts|properties)'
  'scripts/(test-nav3-backstack-entry-mapper-r8|test-nav3-kotlinx-versions|update-kotlinx-serialization-preview|dependency-matrix|nav3-r8-check-relevant)\.sh'
  # Build settings, R8 flags, build logic, the Gradle version, and the root build's settings
  # (which register the version catalogs) and build script, which all builds of the check use.
  'gradle\.properties'
  'settings\.gradle\.kts'
  'build\.gradle\.kts'
  'gradle/wrapper/.*'
  'buildSrc/.*'
  'build-logic/.*'
  # Utilities the mappers use to filter arguments and report integrations.
  'sentry/src/main/java/io/sentry/util/.*'
  '\.github/workflows/integration-tests-nav3-r8\.yml'
)

# Version keys of gradle/libs.versions.toml that the check depends on, including the SDK levels its
# apps are built with.
relevantKeys='kotlin|agp|androidxCompose|composeCompiler|androidxLifecycle|androidxNavigation3|compileSdk|compileSdkMinor|minSdk|targetSdk'
# Module and plugin coordinates the check depends on, which also catches entries that declare
# their versions inline instead of referencing one of the keys above.
relevantCoordinates='org\.jetbrains\.kotlin|com\.android\.|androidx\.compose|androidx\.lifecycle|androidx\.savedstate|androidx\.navigation3|kotlinx-serialization'

changedFiles="$(git diff --name-only "$base" "$head")"

pathPattern="^($(IFS='|'; echo "${RELEVANT_PATHS[*]}"))$"
if grep -q -E "$pathPattern" <<< "$changedFiles"; then
  echo true
  exit 0
fi

if [[ "$anyCatalogChange" == "true" ]] \
  && grep -q -x -F -e "$versionCatalog" -e "$matrixCatalog" <<< "$changedFiles"; then
  echo true
  exit 0
fi

# Prints the added and removed lines of catalog $1, without the diff's file headers.
changedLines() {
  git diff --unified=0 "$base" "$head" -- "$1" | grep -E '^[-+]' | grep -v -E '^(\+\+\+|---) ' || true
}

if grep -q -E "^[-+][[:space:]]*($relevantKeys)[[:space:]]*=|$relevantCoordinates" <<< "$(changedLines "$versionCatalog")" \
  || grep -q -F "kotlinxSerialization" <<< "$(changedLines "$matrixCatalog")"; then
  echo true
else
  echo false
fi
