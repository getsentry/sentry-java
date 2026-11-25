#!/usr/bin/env bash
set -euo pipefail

# Script for testing sentry-android-navigation3 against new kotlinx.serialization release
# candidates. Updates kotlinxSerialization-preview in gradle/dependency-matrix.versions.toml, which
# the Nav3 R8 check and unit tests also run against while it's newer than
# kotlinxSerialization-stable.
#
# Run weekly by update-deps.yml, whose pattern only matches release candidate tags (e.g.
# v1.14.0-RC). Dependabot bumps kotlinxSerialization-stable, and nothing bumps
# kotlinxSerialization-floor. If the resulting PR fails, see "When a kotlinx.serialization bump
# fails" in sentry-android-integration-tests/nav3-backstack-entry-mapper-r8-check/README.md.

cd "$(dirname "$0")/../"
matrix=scripts/dependency-matrix.sh

case "${1:-}" in
get-version)
    # Printed as the release's tag (e.g. v1.12.0-RC), which the updater uses as the old ref when it
    # compares upstream changelogs.
    echo "v$("$matrix" get kotlinxSerialization preview)"
    ;;
get-repo)
    echo "https://github.com/Kotlin/kotlinx.serialization.git"
    ;;
set-version)
    # kotlinx.serialization tags are prefixed with "v" (e.g. v1.12.0-RC).
    version=${2#v}

    echo "Setting kotlinxSerialization-preview to '$version'"
    "$matrix" set kotlinxSerialization preview "$version"
    ;;
*)
    echo "Unknown argument '${1:-}'"
    exit 1
    ;;
esac
