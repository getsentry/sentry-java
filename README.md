<p align="center">
  <a href="https://sentry.io" target="_blank" align="center">
    <img src="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" width="280">
  </a>
  <br />
</p>

Sentry SDK for Java and Android
===========
[![Travis](https://img.shields.io/travis/getsentry/sentry-java/ref/sentry-java-2?label=Travis)](https://travis-ci.com/getsentry/sentry-java)
[![AppVeyor](https://img.shields.io/appveyor/build/sentry/sentry-java?label=AppVeyor)](https://ci.appveyor.com/project/sentry/sentry-java)
[![GH Workflow](https://img.shields.io/github/workflow/status/getsentry/sentry-java/Workflow%20Ubuntu%20macOS?label=GH%20Workflow)](https://github.com/getsentry/sentry-java/actions)
[![Tests](https://img.shields.io/appveyor/tests/sentry/sentry-java/ref/sentry-java-2?compact_message)](https://ci.appveyor.com/project/sentry/sentry-java/branch/ref/sentry-java-2/tests)
[![codecov](https://codecov.io/gh/getsentry/sentry-java/branch/ref/sentry-java-2/graph/badge.svg)](https://codecov.io/gh/getsentry/sentry-java)

|      Packages          | bintray | Android API |
| ---------------------- | ------- | ------- |
| sentry-android | [![sentry-android](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-android)](https://bintray.com/getsentry/maven/io.sentry:sentry-android?tab=overview) | 16 |
| sentry-android-core | [![sentry-android-core](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-android-core)](https://bintray.com/getsentry/maven/io.sentry:sentry-android-core?tab=overview) | 14 |
| sentry-android-ndk | [![sentry-android-ndk](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-android-ndk)](https://bintray.com/getsentry/maven/io.sentry:sentry-android-ndk?tab=overview) | 16 |
| sentry-android-timber | [![sentry-android-timber](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-android-timber)](https://bintray.com/getsentry/maven/io.sentry:sentry-android-timber?tab=overview) | 14 |
| sentry | [![sentry](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry)](https://bintray.com/getsentry/maven/io.sentry:sentry?tab=overview) | 14 |
| sentry-spring-boot-starter | [![sentry-spring-boot-starter](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-spring-boot-starter)](https://bintray.com/getsentry/maven/io.sentry:sentry-spring-boot-starter?tab=overview) | |
| sentry-spring | [![sentry-spring](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-spring)](https://bintray.com/getsentry/maven/io.sentry:sentry-spring?tab=overview) | |
| sentry-logback | [![sentry-logback](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-logback)](https://bintray.com/getsentry/maven/io.sentry:sentry-logback?tab=overview) | |
| sentry-log4j2 | [![sentry-log4j2](https://img.shields.io/bintray/v/getsentry/maven/io.sentry:sentry-log4j2)](https://bintray.com/getsentry/maven/io.sentry:sentry-log4j2?tab=overview) | |


# Java SDK 3.0 Docs

The Java SDK documentation [can be found on docs.sentry.io](https://docs.sentry.io/platforms/java/).

Java SDK version 1.x [can still be found here](https://docs.sentry.io/clients/java/).

# Android Docs

That's the initial page of the release [2.x and its docs](https://docs.sentry.io/platforms/android).

Migration page from [sentry-android 1.x to sentry-android 2.0](https://docs.sentry.io/platforms/android/migration/).

# Blog posts

[New Android SDK How-to](https://blog.sentry.io/2019/12/10/new-android-sdk-how-to).

[Adding Native support to our Android SDK](https://blog.sentry.io/2019/11/25/adding-native-support-to-our-android-sdk).

# Samples

[Sample App. with Sentry Android SDK and Sentry Gradle Plugin](https://github.com/getsentry/examples/tree/master/android).

[Sample for Development](https://github.com/getsentry/sentry-java/tree/main/sentry-samples).

# Development

This repository includes [`sentry-native`](https://github.com/getsentry/sentry-native/) as a git submodule.
To build against `sentry-native` checked-out elsewhere in your file system, create a symlink `sentry-android-ndk/sentry-native-local` that points to your `sentry-native` directory.
For example, if you had `sentry-native` checked-out in a sibling directory to this repo:

`ln -s ../../sentry-native sentry-android-ndk/sentry-native-local`

which will be picked up by `gradle` and used instead of the git submodule.
This directory is also included in `.gitignore` not to be shown as pending changes.

# Resources

* [![Java Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg?label=java%20docs)](https://docs.sentry.io/platforms/java/)
* [![Android Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg?label=android%20docs)](https://docs.sentry.io/platforms/android/)
* [![Forum](https://img.shields.io/badge/forum-sentry-green.svg)](https://forum.sentry.io/c/sdks)
* [![Discord](https://img.shields.io/discord/621778831602221064)](https://discord.gg/Ww9hbqr)
* [![Stack Overflow](https://img.shields.io/badge/stack%20overflow-sentry-green.svg)](http://stackoverflow.com/questions/tagged/sentry)
* [![Code of Conduct](https://img.shields.io/badge/code%20of%20conduct-sentry-green.svg)](https://github.com/getsentry/.github/blob/master/CODE_OF_CONDUCT.md)
* [![Twitter Follow](https://img.shields.io/twitter/follow/getsentry?label=getsentry&style=social)](https://twitter.com/intent/follow?screen_name=getsentry)
