#!/usr/bin/env bash
set -euo pipefail

# Updater for defaultLatestKotlinx in scripts/test-nav3-serialname-r8.sh: the latest
# kotlinx.serialization that the nav3-serialname-r8-check integration app builds against, on top of
# the pinned compile floor (kotlinx-serialization-core-nav3 in gradle/libs.versions.toml). Driven by
# update-deps.yml, the same way scripts/update-sentry-native-ndk.sh keeps the native SDK current.
#
# Only that line is touched; the floor stays pinned.

cd "$(dirname "$0")/../"
SCRIPT_FILEPATH=scripts/test-nav3-serialname-r8.sh

case $1 in
get-version)
    perl -ne 'print "$1\n" if ( m/^defaultLatestKotlinx="([0-9.]+)"/ )' "$SCRIPT_FILEPATH"
    ;;
get-repo)
    echo "https://github.com/Kotlin/kotlinx.serialization.git"
    ;;
set-version)
    version=$2

    echo "Setting defaultLatestKotlinx to '$version'"

    PATTERN='^(defaultLatestKotlinx=")[0-9.]+(")'
    perl -pi -e "s/$PATTERN/\${1}$version\${2}/" "$SCRIPT_FILEPATH"
    ;;
*)
    echo "Unknown argument $1"
    exit 1
    ;;
esac
