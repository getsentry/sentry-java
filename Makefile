.PHONY: clean compile dryRelease doRelease release update stop

all: clean compile update dryRelease

# deep clean
clean:
	./gradlew clean

# build and run tests
compile:
	./gradlew build

# do a dry release (like a local deploy)
dryRelease:
	./gradlew bintrayUpload -PbintrayUser=dryUser -PbintrayKey=dryKey

# deploy the current build to bintray, jcenter and maven central
doRelease:
	./gradlew bintrayUpload -PbintrayUser="$(BINTRAY_USERNAME)" -PbintrayKey="$(BINTRAY_KEY)" -PmavenCentralUser="$(MAVEN_USER)" -PmavenCentralPassword="$(MAVEN_PASS)" -PmavenCentralSync=true -PdryRun=false --info

distZip:
	./gradlew distZip

# deep clean, build and deploy to bintray, jcenter and maven central
release: clean compile dryRelease distZip

# check for dependencies update
update:
	./gradlew dependencyUpdates -Drevision=release

# We stop gradle at the end to make sure the cache folders
# don't contain any lock files and are free to be cached.
stop:
	./gradlew --stop
