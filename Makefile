.PHONY: all clean compile dryRelease update stop checkFormat format api

all: stop clean checkFormat compile dryRelease

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
