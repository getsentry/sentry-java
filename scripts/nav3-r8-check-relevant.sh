#!/usr/bin/env bash
# Prints "true" if the changes between two commits can affect the Nav3 R8 check
# (.github/workflows/integration-tests-nav3-r8.yml), and "false" otherwise.
#
# The workflow runs on pull requests that change gradle/libs.versions.toml, as bumps of the
# toolchain and libraries the check depends on (Kotlin and its serialization plugin, AGP and its
# R8, Compose, lifecycle, savedstate, Nav3) can change how our mappers behave under R8. Most of
# that file's changes bump unrelated dependencies, though, so for those the check is skipped. Any
# other change that triggers the workflow is relevant.
#
# A relevant version that matches none of the patterns below is still checked once the change
# reaches main, as pushes to main run the check on any change to gradle/libs.versions.toml.
#
# Usage: nav3-r8-check-relevant.sh BASE HEAD
set -euo pipefail

base="${1:?Usage: $0 BASE HEAD}"
head="${2:?Usage: $0 BASE HEAD}"
versionCatalog="gradle/libs.versions.toml"

# Version keys of gradle/libs.versions.toml that the check depends on.
relevantKeys='kotlin|agp|androidxCompose|composeCompiler|androidxLifecycle|androidxNavigation3'
# Module and plugin coordinates the check depends on, which also catches entries that declare
# their versions inline instead of referencing one of the keys above.
relevantCoordinates='org\.jetbrains\.kotlin|com\.android\.|androidx\.compose|androidx\.lifecycle|androidx\.savedstate|androidx\.navigation3|kotlinx-serialization'

changedFiles="$(git diff --name-only "$base" "$head")"

# Changed files other than the version catalog trigger the workflow only if they match its
# pull_request paths. Keep this pattern in sync with them.
if grep -v -x -F "$versionCatalog" <<< "$changedFiles" | grep -q -E \
  '^(sentry-android-navigation3/|sentry-android-integration-tests/(nav3-backstack-entry-mapper-r8-check|nav3-no-kotlinx-r8-check)/|sentry-android-integration-tests/[^/]*\.gradle\.kts$|scripts/(test-nav3-backstack-entry-mapper-r8|update-nav3-kotlinx-serialization|nav3-r8-check-relevant)\.sh$|gradle/nav3\.versions\.toml$|\.github/workflows/integration-tests-nav3-r8\.yml$)'; then
  echo true
  exit 0
fi

# Added and removed lines of the version catalog, without the diff's file headers.
changedLines="$(git diff --unified=0 "$base" "$head" -- "$versionCatalog" | grep -E '^[-+]' | grep -v -E '^(\+\+\+|---) ' || true)"

if grep -q -E "^[-+][[:space:]]*($relevantKeys)[[:space:]]*=|$relevantCoordinates" <<< "$changedLines"; then
  echo true
else
  echo false
fi
