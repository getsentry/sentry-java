.PHONY: all clean compile javadocs dryRelease update stop checkFormat format api assembleBenchmarkTestRelease assembleUiTestRelease assembleUiTestCriticalRelease createCoverageReports runUiTestCritical buildMockRelay check preMerge publish

all: stop clean javadocs compile createCoverageReports
assembleBenchmarks: assembleBenchmarkTestRelease
assembleUiTests: assembleUiTestRelease
preMerge: check createCoverageReports
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

# We stop gradle at the end to make sure the cache folders
# don't contain any lock files and are free to be cached.
stop:
	./gradlew --stop

# Spotless check's code
checkFormat:
	./gradlew spotlessJavaCheck spotlessKotlinCheck

# Spotless format's code
format:
	./gradlew spotlessApply

# Binary compatibility validator
api:
	./gradlew apiDump

# Assemble release and Android test apk of the uitest-android-benchmark module
assembleBenchmarkTestRelease:
	./gradlew :sentry-android-integration-tests:sentry-uitest-android-benchmark:assembleRelease
	./gradlew :sentry-android-integration-tests:sentry-uitest-android-benchmark:assembleAndroidTest -DtestBuildType=release

# Assemble release and Android test apk of the uitest-android module
assembleUiTestRelease:
	./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleRelease
	./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleAndroidTest -DtestBuildType=release

# Assemble release of the uitest-android-critical module
assembleUiTestCriticalRelease:
	./gradlew :sentry-android-integration-tests:sentry-uitest-android-critical:assembleRelease

# Run Maestro tests for the uitest-android-critical module
runUiTestCritical:
	./scripts/test-ui-critical.sh

# Build the mock relay for critical tests
buildMockRelay:
	./gradlew :sentry-android-integration-tests:sentry-mock-relay:build

# Create coverage reports
#  - Jacoco for Java & Android modules
#  - Kover for KMP modules e.g sentry-compose
createCoverageReports:
	./gradlew jacocoTestReport
	./gradlew koverXmlReportRelease

# Run tests and lint
check:
	./gradlew check
