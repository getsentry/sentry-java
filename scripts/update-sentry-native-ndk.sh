#!/usr/bin/env bash
set -euo pipefail

cd $(dirname "$0")/../
GRADLE_NDK_FILEPATH=sentry-android-ndk/build.gradle.kts
GRADLE_SAMPLE_FILEPATH=sentry-samples/sentry-samples-android/build.gradle.kts

case $1 in
get-version)
    version=$(perl -ne 'print "$1\n" if ( m/io\.sentry:sentry-native-ndk:([0-9.]+)+/ )' $GRADLE_NDK_FILEPATH)

    echo "v$version"
    ;;
get-repo)
    echo "https://github.com/getsentry/sentry-native.git"
    ;;
set-version)
    version=$2

    # Remove leading "v"
    if [[ "$version" == v* ]]; then
        version="${version:1}"
    fi

    echo "Setting sentry-native-ndk version to '$version'"

    PATTERN="io\.sentry:sentry-native-ndk:([0-9.]+)+"
    perl -pi -e "s/$PATTERN/io.sentry:sentry-native-ndk:$version/g" $GRADLE_NDK_FILEPATH
    perl -pi -e "s/$PATTERN/io.sentry:sentry-native-ndk:$version/g" $GRADLE_SAMPLE_FILEPATH
    ;;
*)
    echo "Unknown argument $1"
    exit 1
    ;;
esac
