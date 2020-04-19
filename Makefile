.PHONY: clean compile dryRelease doRelease release update

all: clean compile update dryRelease

# deep clean
clean:
	./gradlew clean cleanBuildCache

# build and run tests
compile:
	./gradlew build

# do a dry release (like a local deploy)
dryRelease:
	./gradlew bintrayUpload -PbintrayUser=dryUser -PbintrayKey=dryKey

# deploy the current build to bintray, jcenter and maven central
doRelease:
	./gradlew bintrayUpload -PbintrayUser="$(BINTRAY_USERNAME)" -PbintrayKey="$(BINTRAY_KEY)" -PmavenCentralUser="$(MAVEN_USER)" -PmavenCentralPassword="$(MAVEN_PASS)" -PmavenCentralSync=true -PdryRun=false

# deep clean, build and deploy to bintray, jcenter and maven central
release: clean compile dryRelease doRelease

# check for dependencies update
update:
	./gradlew dependencyUpdates -Drevision=release
