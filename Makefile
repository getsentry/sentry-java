.PHONY: all, clean compile dryRelease doRelease release update stop checkFormat format

all: clean checkFormat compile dryRelease

# deep clean
clean:
	./gradlew clean

# build and run tests
compile:
	./gradlew build

# do a dry release (like a local deploy)
# remember to add the -SNAPSHOT suffix to the version
dryRelease:
	./gradlew publishToMavenLocal --no-daemon

# deploy the current build to maven central
# remember to remove the -SNAPSHOT suffix from the version
# promotes the release to maven central
doRelease:
	./gradlew publish --no-daemon
	./gradlew closeAndReleaseRepository

# clean, build, deploy and promote to maven central
release: clean checkFormat compile doRelease

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
