#!/bin/bash
set -eux

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR/..

OLD_VERSION="${1}"
NEW_VERSION="${2}"

# Add a new unreleased entry in the changelog
sed -i "" 's/# Changelog/# Changelog\n\n## Unreleased/' CHANGELOG.md
