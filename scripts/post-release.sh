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

# Increment `versionName` and make it a snapshot
VERSION_NAME_PATTERN="versionName"
version="$( awk "/$VERSION_NAME_PATTERN/" $GRADLE_FILEPATH | egrep -o '[0-9].*$' )" # from the first digit until the end
version_digit_to_bump="$( awk "/$VERSION_NAME_PATTERN/" $GRADLE_FILEPATH | egrep -o '[0-9]+$')"
((version_digit_to_bump++))
# Using `*` instead of `+` for compatibility. The result is the same,
# since the version to be bumped is extracted using `+`.
new_version="$( echo $version | sed "s/[0-9]*$/$version_digit_to_bump/g" )"
sed -i "" -e "s/$VERSION_NAME_PATTERN=.*$/$VERSION_NAME_PATTERN=$new_version-SNAPSHOT/g" $GRADLE_FILEPATH
