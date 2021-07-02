#!/bin/bash
set -eux

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR/..

OLD_VERSION="${1}"
NEW_VERSION="${2}"

# Add a new unreleased entry in the changelog
sed -i "" 's/# Changelog/# Changelog\n\n## Unreleased/' CHANGELOG.md

# Increment `buildVersionCode`
VERSION_CODE_PATTERN="buildVersionCode"
VERSION_NUMBER="$( awk "/$VERSION_CODE_PATTERN/" $GRADLE_FILEPATH | grep -o '[0-9]\+' )"
((VERSION_NUMBER++))
sed -ie "s/$VERSION_CODE_PATTERN=.*$/$VERSION_CODE_PATTERN=$VERSION_NUMBER/g" $GRADLE_FILEPATH
