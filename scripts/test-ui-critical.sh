#!/usr/bin/env bash
set -e

echo "Checking if ADB is installed..."
if ! command -v adb &> /dev/null; then
    echo "ADB is not installed or not in PATH. Please install Android SDK platform tools and ensure ADB is in your PATH."
    exit 1
fi

echo "Checking if an Android emulator is running..."
if ! adb devices | grep -q "emulator"; then
    echo "No Android emulator is currently running. Please start an emulator before running this script."
    exit 1
fi

echo "Checking if Maestro is installed..."
if ! command -v maestro &> /dev/null; then
    echo "Maestro is not installed. Please install Maestro before running this script."
    exit 1
fi

echo "Building the UI Test Critical app..."
make assembleUiTestCriticalRelease

echo "Installing the UI Test Critical app on the emulator..."
baseDir="sentry-android-integration-tests/sentry-uitest-android-critical"
buildDir="build/outputs/apk/release"
apkName="sentry-uitest-android-critical-release.apk"
appPath="${baseDir}/${buildDir}/${apkName}"
adb install -r -d "$appPath"

echo "Running the Maestro tests..."
maestro test \
  "${baseDir}/maestro" \
  --debug-output "${baseDir}/maestro-logs"
