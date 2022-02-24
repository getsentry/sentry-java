#!/bin/bash

# ./scripts/bump-version.sh <old version> <new version>
# eg ./scripts/bump-version.sh "6.0.0-alpha.1" "6.0.0-alpha.2"

set -eux

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR/..

OLD_VERSION="$1"
NEW_VERSION="$2"

GRADLE_FILEPATH="gradle.properties"

# Replace `versionName` with the given version
VERSION_NAME_PATTERN="versionName"
perl -pi -e "s/$VERSION_NAME_PATTERN=.*$/$VERSION_NAME_PATTERN=$NEW_VERSION/g" $GRADLE_FILEPATH
