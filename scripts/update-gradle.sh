#!/usr/bin/env bash
set -euo pipefail

cd $(dirname "$0")/../

if [[ -n ${CI+x} ]]; then
    export JAVA_HOME=$JAVA_HOME_17_X64
fi

case $1 in
get-version)
    # `./gradlew` shows some info on the first run, breaking the parsing in the next step.
    # Therefore, we run it once without checking any output.
    ./gradlew --version >/dev/null
    version="$(./gradlew --version | sed -E -n 's/.*Gradle +([0-9.]+).*/\1/p')"

    # Add trailing ".0" - gradlew outputs '7.1' instead of '7.1.0'
    if [[ "$version" =~ ^[0-9]\.[0-9]$ ]]; then
        version="$version.0"
    fi

    echo "v$version"
    ;;
get-repo)
    echo "https://github.com/gradle/gradle.git"
    ;;
set-version)
    version=$2

    # Remove leading "v"
    if [[ "$version" == v* ]]; then
        version="${version:1}"
    fi

    echo "Setting gradle version to '$version'"

    # This sets version to gradle-wrapper.properties.
    ./gradlew wrapper --gradle-version "$version"

    # Verify it works.
    ./gradlew --version
    ;;
*)
    echo "Unknown argument $1"
    exit 1
    ;;
esac
