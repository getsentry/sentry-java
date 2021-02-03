.PHONY: clean compile dryRelease doRelease release update stop checkFormat

all: clean checkFormat compile dryRelease

# deep clean
clean:
	./gradlew clean

# build and run tests
compile:
	./gradlew build

# do a dry release (like a local deploy)
dryRelease:
	./gradlew bintrayUpload -PbintrayUser=dryUser -PbintrayKey=dryKey

# deploy the current build to bintray, jcenter and maven central, upload debug symbols to symbol collector
doRelease:
	# bintray/jcenter upload
	./gradlew bintrayUpload -PbintrayUser="$(BINTRAY_USERNAME)" -PbintrayKey="$(BINTRAY_KEY)" -PdryRun=false --info

	# upload symbols
	cd sentry-android-ndk
	# be sure SymbolCollector.Console is in your $PATH - https://github.com/getsentry/symbol-collector/releases
	SymbolCollector.Console --upload directory --path ./ --batch-type android --bundle-id sentry-android-ndk-$VERSION --server-endpoint https://symbol-collector.services.sentry.io
	cd ..

	# maven central sync using jfrog cli - https://jfrog.com/getcli/
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-log4j2/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-logback/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-servlet/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-spring/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-apache-http-client-5/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-spring-boot-starter/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-java/io.sentry:sentry-jul/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-android/io.sentry:sentry-android/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-android/io.sentry:sentry-android-core/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-android/io.sentry:sentry-android-ndk/$VERSION
	jfrog bt mcs --user=$BINTRAY_USERNAME --key=$BINTRAY_KEY --sonatype-username=$MAVEN_USER --sonatype-password=$MAVEN_PASS getsentry/sentry-android/io.sentry:sentry-android-timber/$VERSION

distZip:
	./gradlew distZip

# deep clean, build and deploy to bintray, jcenter and maven central
release: clean checkFormat compile dryRelease distZip

# check for dependencies update
update:
	./gradlew dependencyUpdates -Drevision=release

# We stop gradle at the end to make sure the cache folders
# don't contain any lock files and are free to be cached.
stop:
	./gradlew --stop

checkFormat:
	./gradlew spotlessJavaCheck spotlessKotlinCheck

format:
	./gradlew spotlessApply
