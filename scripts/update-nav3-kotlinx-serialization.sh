#!/usr/bin/env bash
set -euo pipefail

# Script for keeping the Nav3 R8 test up-to-date with new kotlinx.serialization releases.
# Updates defaultLatestKotlinx in scripts/test-nav3-backstack-entry-mapper-r8.sh, which
# adds that version to the test matrix alongside the SDK's pinned compile version.
#
# Run weekly by update-deps.yml. Does not change kotlinxSerializationFloor in
# gradle/nav3.versions.toml.

cd "$(dirname "$0")/../"
SCRIPT_FILEPATH=scripts/test-nav3-backstack-entry-mapper-r8.sh

case $1 in
get-version)
    # Printed as the release's tag (e.g. v1.13.0), which the updater uses as the old ref when it
    # compares upstream changelogs.
    perl -ne 'print "v$1\n" if ( m/^defaultLatestKotlinx="([0-9.]+)"/ )' "$SCRIPT_FILEPATH"
    ;;
*)
    echo "Unknown argument $1"
    exit 1
    ;;
esac
