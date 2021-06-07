#!/bin/bash
set -eux

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR/..

OLD_VERSION="$1"
NEW_VERSION="$2"

echo $OLD_VERSION
echo $NEW_VERSION

GRADLE_FILEPATH="gradle.properties"

# Increment `buildVersionCode`
VERSION_CODE_PATTERN="buildVersionCode"
VERSION_NUMBER="$( awk "/$VERSION_CODE_PATTERN/" $GRADLE_FILEPATH | grep -o '[0-9]\+' )"
((VERSION_NUMBER++))
sed -ie "s/$VERSION_CODE_PATTERN=.*$/$VERSION_CODE_PATTERN=$VERSION_NUMBER/g" $GRADLE_FILEPATH
echo "buildVersionCode done"

# Replace `versionName` with the given version
VERSION_NAME_PATTERN="versionName"
sed -ie "s/$VERSION_NAME_PATTERN=.*$/$VERSION_NAME_PATTERN=$NEW_VERSION/g" $GRADLE_FILEPATH
echo "versionName done"
