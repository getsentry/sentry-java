# Contributing to sentry-java

We love pull requests from everyone.
We suggest opening an issue to discuss bigger changes before investing on a big PR.

# Requirements

The project requires you to run JDK 17.

## Android

This repository is a monorepo which includes Java and Android libraries.
If you'd like to contribute to Java and don't have an Android SDK with NDK installed,
you can remove the Android libraries from `settings.gradle.kts` to make sure you can build the project.

# Git commit hook:

Optionally, you can install spotlessCheck pre-commit hook:

```shell
git config core.hooksPath hooks/
```

To run the build and tests:

```shell
make compile
```

# Format

To format the changed code and make CI happy you can run:

```shell
make format
```

or

```shell
./gradlew spotlessApply
```

# Binary compatibility validation

To prevent breaking ABI changes and exposing things we should not, we make use of https://github.com/Kotlin/binary-compatibility-validator. If your change intended to introduce a new public method/property or modify the existing one you can overwrite the API declarations to make CI happy as follows (overwrites them from scratch):

```shell
make api
```

or 

```shell
./gradlew apiDump
```

However, if your change did not intend to modify the public API, consider changing the method/property visibility or removing the change altogether.

# CI

Build and tests are automatically run against branches and pull requests
via GH Actions.
