#!/bin/bash

# ./scripts/post-release.sh <old version> <new version>
# eg ./scripts/post-release.sh "6.0.0-alpha.1" "6.0.0-alpha.2"

set -eux

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR/..

OLD_VERSION="${1}"
NEW_VERSION="${2}"

git checkout 6.x.x
GRADLE_FILEPATH="gradle.properties"

# Add a new unreleased entry in the changelog
sed -i "" 's/# Changelog/# Changelog\n\n## Unreleased/' CHANGELOG.md

# Increment `versionName` and make it a snapshot
# Incrementing the version name before the release (`bump-version.sh`) sets a
# fixed version until the next release it's made. For testing purposes, it's
# interesting to have a different version name that doesn't match the
# name of the version in production.
# Note that the version must end with a number: `1.2.3-alpha` is a semantic
# version but not compatible with this post-release script.
# and `1.2.3-alpha.0` should be used instead.
VERSION_NAME_PATTERN="versionName"
version="$( awk "/$VERSION_NAME_PATTERN/" $GRADLE_FILEPATH | egrep -o '[0-9].*$' )" # from the first digit until the end
version_digit_to_bump="$( awk "/$VERSION_NAME_PATTERN/" $GRADLE_FILEPATH | egrep -o '[0-9]+$')"
((version_digit_to_bump++))
# Using `*` instead of `+` for compatibility. The result is the same,
# since the version to be bumped is extracted using `+`.
new_version="$( echo $version | sed "s/[0-9]*$/$version_digit_to_bump/g" )"
sed -i "" -e "s/$VERSION_NAME_PATTERN=.*$/$VERSION_NAME_PATTERN=$new_version-SNAPSHOT/g" $GRADLE_FILEPATH

git add .
git commit -m "Prepare $new_version"
git push
