.PHONY: checkstyle compile test install clean prepare prepareDocs prepareMvn prepareChanges perform verify

MVN=mvn -e

all: checkstyle test install

compile:
	$(MVN) compile

checkstyle:
	$(MVN) checkstyle:check

verify:
	$(MVN) verify

test: verify

install:
	$(MVN) install -Dcheckstyle.skip=true -DskipTests

clean:
	$(MVN) clean

prepareDocs:
# Store previously released version
	$(eval PREVIOUS_RELEASE=$(shell grep -E "^Version" CHANGES | head -2 | tail -1 | sed -e 's/Version //'))
	@echo Previous release: $(PREVIOUS_RELEASE)
# Store release project version
	$(eval RELEASE_VERSION=$(shell mvn help:evaluate -Dexpression=project.version | grep -Ev '^\[' | sed -e 's/-SNAPSHOT//'))
	@echo This release: $(RELEASE_VERSION)
# Fix released version in documentation
	@echo Fixing documentation versions
	find . \( -name '*.md' -or -name '*.rst' \) -exec sed -i -e 's/$(PREVIOUS_RELEASE)/$(RELEASE_VERSION)/g' {} \;
# Commit documentation changes
	@echo Committing documentation version changes
	git commit -a -m 'Bump docs to $(RELEASE_VERSION)'

prepareMvn:
# Prepare release (interactive)
	$(MVN) release:prepare

prepareChanges:
# Store new project version
	$(eval DEV_VERSION=$(shell mvn help:evaluate -Dexpression=project.version | grep -Ev '^\[' | sed -e 's/-SNAPSHOT//'))
	@echo Development version: $(DEV_VERSION)
# Store enough dashes to go under "Version X.Y.Z", accounting for changes in the $VERSION length
	$(eval DASHES=$(shell python -c 'print("-" * (8 + len("$(DEV_VERSION)")))'))
# Add new Version section to the top of the CHANGES file
	@echo Updating CHANGES file
	echo -e "Version $(DEV_VERSION)\n$(DASHES)\n\n-\n" > CHANGES.new && cat CHANGES >> CHANGES.new && mv CHANGES.new CHANGES
	git add CHANGES
	git commit -m "Bump CHANGES to $(DEV_VERSION)"

# Prepare is broken into stages because otherwise `make` will run things out of order
prepare: prepareDocs prepareMvn prepareChanges

perform:
	$(MVN) release:perform

rollback:
	$(MVN) release:rollback
