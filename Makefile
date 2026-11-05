.PHONY: all clean compile javadocs dryRelease update checkFormat api assembleUiTestRelease assembleUiTestCriticalRelease runUiTestCritical check preMerge publish

all: stop clean javadocs compile
assembleUiTests: assembleUiTestRelease
preMerge: check
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

# Binary compatibility validator
api:
	./gradlew updateKotlinAbi

# Assemble release and Android test apk of the uitest-android module
assembleUiTestRelease:
	./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleRelease :sentry-android-integration-tests:sentry-uitest-android:assembleAndroidTest

# Assemble release of the uitest-android-critical module
assembleUiTestCriticalRelease:
	./gradlew :sentry-android-integration-tests:sentry-uitest-android-critical:assembleRelease

# Run Maestro tests for the uitest-android-critical module
runUiTestCritical:
	./scripts/test-ui-critical.sh

# Run tests and lint
check:
	./gradlew check
