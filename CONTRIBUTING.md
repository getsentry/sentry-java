<p align="center">
  <a href="https://sentry.io" target="_blank" align="center">
    <img src="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" width="280">
  </a>
  <br />
</p>

# Contributing

Thanks for your interest in contributing to `sentry-java`! This guide explains how we work with external contributions and how to get your environment set up. If you have questions, reach out on [Discord](https://discord.com/invite/sentry).

# Submitting Changes

**TL;DR: Discuss your change in a GitHub issue before opening a pull request. We may close PRs that weren't discussed with a maintainer first.**

Every line we merge is code we maintain long-term for hundreds of thousands of apps, so we want to agree on the problem and the approach before anyone writes code. This saves time for you and for us.

## Before You Start

1. **Find or create an issue.** Search the [issue tracker](https://github.com/getsentry/sentry-java/issues) first. If nothing fits, open a new issue describing the problem or feature.
2. **Discuss your approach with a maintainer.** Comment on the issue with what you plan to change and wait for a maintainer to respond before you start working.
3. **Check the assignee.** If the issue is already assigned to someone else, coordinate with them in the issue before starting work.

## Making Your Contribution

- Fork the repo and make your changes on a branch.
- Add tests. Non-trivial PRs won't be accepted without them.
- Don't bump version numbers. We do that when we release.
- Open a PR that links the issue (e.g. `Fixes #123`)
- Name your PR according to our [commit message format](https://develop.sentry.dev/commit-messages/#commit-message-format).
- Keep PRs focused: one change per PR, no unrelated refactors.

## AI Use

You are welcome to use whatever tools you prefer for making a contribution. However, any changes you propose have to be understood, reviewed and tested by you, a human, first, before you submit a pull request with them for the Sentry team to review. If we feel like that didn't happen, we will close the PR outright. For example, we won't review visibly AI-generated PRs from an agent instructed to look for and "fix" open issues in the repo. This aligns with our SDK principle: [every line has an owner](https://develop.sentry.dev/sdk/getting-started/principles/#every-line-has-an-owner).

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

# Linking issues

If a PR should notify a linked issue after release, use a GitHub closing keyword in the PR
description, such as `Fixes #123`, `Closes #123`, or `Resolves #123`. Release notification
automation only comments on issues GitHub recognizes as closed by the released PR; mentioning an
issue without a closing keyword is not enough.

# CI

Build and tests are automatically run against branches and pull requests
via GH Actions.
