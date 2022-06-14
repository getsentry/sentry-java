.PHONY: all clean compile dryRelease update stop checkFormat format api assembleBenchmarkTestRelease assembleUiTestRelease

all: stop clean checkFormat compile dryRelease
assembleBenchmarks: stop clean assembleBenchmarkTestRelease
assembleUiTests: stop clean assembleUiTestRelease

# deep clean
clean:
	./gradlew clean
	rm -rf distributions

# build and run tests
compile:
	./gradlew build

# do a dry release (like a local deploy)
dryRelease:
	./gradlew publishToMavenLocal --no-daemon --no-parallel

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
