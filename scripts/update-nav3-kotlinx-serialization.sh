#!/usr/bin/env bash
set -euo pipefail

# Script for keeping the Nav3 R8 test up-to-date with new kotlinx.serialization releases.
# Updates defaultLatestKotlinx in scripts/test-nav3-backstack-entry-mapper-r8.sh, which
# adds that version to the test matrix alongside the SDK's pinned compile version.
#
# Run weekly by update-deps.yml. Does not change kotlinxSerializationFloor in
# gradle/nav3.versions.toml. If the resulting PR fails, see "When the weekly kotlinx.serialization
# bump fails" in sentry-android-integration-tests/nav3-backstack-entry-mapper-r8-check/README.md.

cd "$(dirname "$0")/../"
SCRIPT_FILEPATH=scripts/test-nav3-backstack-entry-mapper-r8.sh

case $1 in
get-version)
    # Printed as the release's tag (e.g. v1.13.0), which the updater uses as the old ref when it
    # compares upstream changelogs.
    perl -ne 'print "v$1\n" if ( m/^defaultLatestKotlinx="([0-9.]+)"/ )' "$SCRIPT_FILEPATH"
    ;;
get-repo)
    echo "https://github.com/Kotlin/kotlinx.serialization.git"
    ;;
set-version)
    # kotlinx.serialization tags are prefixed with "v" (e.g. v1.13.0).
    version=${2#v}

    echo "Setting defaultLatestKotlinx to '$version'"

    PATTERN='^(defaultLatestKotlinx=")[0-9.]+(")'
    perl -pi -e "s/$PATTERN/\${1}$version\${2}/" "$SCRIPT_FILEPATH"
    ;;
*)
    echo "Unknown argument $1"
    exit 1
    ;;
esac
