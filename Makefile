.PHONY: all clean compile javadocs dryRelease update checkFormat api assembleBenchmarkTestRelease assembleUiTestRelease assembleUiTestCriticalRelease createCoverageReports runUiTestCritical setupPython systemTest systemTestInteractive check preMerge publish

all: stop clean javadocs compile createCoverageReports
assembleBenchmarks: assembleBenchmarkTestRelease
assembleUiTests: assembleUiTestRelease
preMerge: check createCoverageReports
publish: clean dryRelease

# deep clean
clean:
	./gradlew clean --no-configuration-cache
	rm -rf distributions
	rm -rf .venv

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

# Create coverage reports
#  - Jacoco for Java & Android modules
#  - Kover for KMP modules e.g sentry-compose
createCoverageReports:
	./gradlew jacocoTestReport
	./gradlew koverXmlReportRelease

# Create the Python virtual environment for system tests, and install the necessary dependencies
setupPython:
	@test -d .venv || (python3 -m venv .venv && .venv/bin/pip install --upgrade pip && .venv/bin/pip install -r requirements.txt)

# Run system tests for sample applications
systemTest: setupPython
	.venv/bin/python test/system-test-runner.py test --all

# Run system tests with interactive module selection
systemTestInteractive: setupPython
	.venv/bin/python test/system-test-runner.py test --interactive

# Run tests and lint
check:
	./gradlew check
