#!/usr/bin/env bash
# Prints "true" if the changes between two commits can affect the Nav3 R8 check
# (.github/workflows/integration-tests-nav3-r8.yml), and "false" otherwise.
#
# The workflow runs on pull requests that change gradle/libs.versions.toml, as bumps of the
# toolchain and libraries the check depends on (Kotlin and its serialization plugin, AGP and its
# R8, Compose, lifecycle, savedstate, Nav3) can change how our mappers behave under R8. Most of
# that file's changes bump unrelated dependencies, though, so for those the check is skipped.
#
# It also runs on pull requests that change gradle/dependency-matrix.versions.toml, which holds the
# kotlinx.serialization versions the check runs against, and may hold other matrices' versions.
# Only changes to lines that name the kotlinxSerialization matrix are relevant.
#
# Any other change that triggers the workflow is relevant.
#
# A relevant version that matches none of the patterns below is still checked once the change
# reaches main, as pushes to main run the check on any change to either catalog.
#
# Usage: nav3-r8-check-relevant.sh BASE HEAD
set -euo pipefail

base="${1:?Usage: $0 BASE HEAD}"
head="${2:?Usage: $0 BASE HEAD}"
versionCatalog="gradle/libs.versions.toml"
matrixCatalog="gradle/dependency-matrix.versions.toml"

# Version keys of gradle/libs.versions.toml that the check depends on.
relevantKeys='kotlin|agp|androidxCompose|composeCompiler|androidxLifecycle|androidxNavigation3'
# Module and plugin coordinates the check depends on, which also catches entries that declare
# their versions inline instead of referencing one of the keys above.
relevantCoordinates='org\.jetbrains\.kotlin|com\.android\.|androidx\.compose|androidx\.lifecycle|androidx\.savedstate|androidx\.navigation3|kotlinx-serialization'

changedFiles="$(git diff --name-only "$base" "$head")"

# Changed files other than the two catalogs trigger the workflow only if they match its
# pull_request paths. Keep this pattern in sync with them.
if grep -v -x -F -e "$versionCatalog" -e "$matrixCatalog" <<< "$changedFiles" | grep -q -E \
  '^(sentry-android-navigation3/|sentry-android-integration-tests/(nav3-backstack-entry-mapper-r8-check|nav3-no-kotlinx-r8-check)/|sentry-android-integration-tests/[^/]*\.gradle\.kts$|scripts/(test-nav3-backstack-entry-mapper-r8|update-kotlinx-serialization-preview|dependency-matrix|nav3-r8-check-relevant)\.sh$|gradle\.properties$|buildSrc/|build-logic/|sentry/src/main/java/io/sentry/util/|\.github/workflows/integration-tests-nav3-r8\.yml$)'; then
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
