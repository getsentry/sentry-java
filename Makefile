.PHONY: all clean compile javadocs dryRelease update checkFormat api assembleUiTestRelease assembleUiTestCriticalRelease runUiTestCritical check checkSampleAndroid checkIntegrationTests preMerge publish

all: stop clean javadocs compile
assembleUiTests: assembleUiTestRelease
preMerge: check checkSampleAndroid checkIntegrationTests
publish: clean dryRelease

# deep clean
clean:
	./gradlew clean --no-configuration-cache
	rm -rf distributions

# build and run tests
compile:
	./gradlew build

javadocs:
	./gradlew aggregateJavadocs

# do a dry release (like a local deploy)
dryRelease:
	./gradlew aggregateJavadocs distZip --no-build-cache --no-configuration-cache

# check for dependencies update
update:
	./gradlew dependencyUpdates -Drevision=release

# Spotless check's code
checkFormat:
	./gradlew spotlessJavaCheck spotlessKotlinCheck
	./gradlew -p sentry-samples/sentry-samples-android spotlessJavaCheck spotlessKotlinCheck
	./gradlew -p sentry-android-integration-tests spotlessJavaCheck spotlessKotlinCheck

# Binary compatibility validator
api:
	./gradlew updateKotlinAbi

# Assemble release and Android test apk of the uitest-android module
assembleUiTestRelease:
	./gradlew -p sentry-android-integration-tests :sentry-uitest-android:assembleRelease :sentry-uitest-android:assembleAndroidTest

# Assemble release of the uitest-android-critical module
assembleUiTestCriticalRelease:
	./gradlew -p sentry-android-integration-tests :sentry-uitest-android-critical:assembleRelease

# Run Maestro tests for the uitest-android-critical module
runUiTestCritical:
	./scripts/test-ui-critical.sh

# Run tests and lint
check:
	./gradlew check

# The Android sample is a separate build, so the root `check` doesn't reach it
checkSampleAndroid:
	./gradlew -p sentry-samples/sentry-samples-android check

# The Android integration tests are a separate build, so the root `check` doesn't reach them
checkIntegrationTests:
	./gradlew -p sentry-android-integration-tests check
