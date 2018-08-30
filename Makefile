# Temporary: Required for macOS release
# export GPG_TTY=`tty`
# eval $(gpg-agent --daemon)

# the test suite currently only works with 1.7.0_80
# https://github.com/getsentry/sentry-java/issues/478

.PHONY: checkstyle compile test install clean prepare prepareMvn prepareChanges perform verify

# TODO: Fix to work between macOS and Linux
MVN=mvn -e -Dhttps.protocols=TLSv1.2
ECHO=echo
SED=sed

all: checkstyle test install

compile:
	$(MVN) compile

checkstyle:
	$(MVN) checkstyle:check

verify:
	$(MVN) verify

test: verify

install:
	$(MVN) source:jar install -Dcheckstyle.skip=true -DskipTests -Dmaven.javadoc.skip=true -B -V

clean:
	$(MVN) clean

prepareMvn:
# Prepare release (interactive)
	$(MVN) release:prepare

prepareChanges:
# Store new project version
	$(eval DEV_VERSION=$(shell mvn help:evaluate -Dexpression=project.version | grep -Ev '^\[' | $(SED) -e 's/-SNAPSHOT//'))
	@echo Development version: $(DEV_VERSION)
# Store enough dashes to go under "Version X.Y.Z", accounting for changes in the $VERSION length
	$(eval DASHES=$(shell python -c 'print("-" * (8 + len("$(DEV_VERSION)")))'))
# Add new Version section to the top of the CHANGES file
	@echo Updating CHANGES file
	$(ECHO) -e "Version $(DEV_VERSION)\n$(DASHES)\n\n-\n" > CHANGES.new && cat CHANGES >> CHANGES.new && mv CHANGES.new CHANGES
	git add CHANGES
	git commit -m "Bump CHANGES to $(DEV_VERSION)"

change-version:
	$(MVN) release:update-versions

# Prepare is broken into stages because otherwise `make` will run things out of order
prepare: prepareMvn prepareChanges

perform:
	$(MVN) release:perform

rollback:
	$(MVN) release:rollback
