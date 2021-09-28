<p align="center">
  <a href="https://sentry.io" target="_blank" align="center">
    <img src="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" width="280">
  </a>
  <br />
</p>

_Bad software is everywhere, and we're tired of it. Sentry is on a mission to help developers write better software faster, so we can get back to enjoying technology. If you want to join us [<kbd>**Check out our open positions**</kbd>](https://sentry.io/careers/)_

Sentry SDK for Java and Android
===========
[![GH Workflow](https://img.shields.io/github/workflow/status/getsentry/sentry-java/Workflow%20Ubuntu%20macOS?label=GH%20Workflow)](https://github.com/getsentry/sentry-java/actions)
[![codecov](https://codecov.io/gh/getsentry/sentry-java/branch/ref/sentry-java-2/graph/badge.svg)](https://codecov.io/gh/getsentry/sentry-java)
[![Discord Chat](https://img.shields.io/discord/621778831602221064?logo=discord&logoColor=ffffff&color=7389D8)](https://discord.gg/PXa5Apfe7K)  

|      Packages          | Maven Central | Android API |
| ---------------------- | ------- | ------- |
| sentry-android | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android) | 16 |
| sentry-android-core | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-core) | 14 |
| sentry-android-ndk | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-ndk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-ndk) | 16 |
| sentry-android-okhttp | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-okhttp/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-okhttp) | 21 |
| sentry-android-timber | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-timber/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-timber) | 14 |
| sentry-android-fragment | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-fragment/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-fragment) | 14 |
| sentry-apache-http-client-5 | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apache-http-client-5/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apache-http-client-5) |
| sentry | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry) | 14 |
| sentry-jul | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-jul/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-jul) |
| sentry-apollo | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apollo/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apollo) |
| sentry-kotlin-extensions | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-kotlin-extensions/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-kotlin-extensions) |
| sentry-servlet  | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-servlet/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-servlet) | |
| sentry-spring-boot-starter | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-starter/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-starter) |
| sentry-spring | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring) |
| sentry-logback | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-logback/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-logback) |
| sentry-log4j2 | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-log4j2/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-log4j2) |



# Useful links and docs

Current Javadocs [generated from source code](https://getsentry.github.io/sentry-java/).

Java SDK version 1.x [can still be found here](https://docs.sentry.io/clients/java/).

Migration page from [sentry-android 1.x and 2.x to sentry-android 4.x](https://docs.sentry.io/platforms/android/migration/).

Migration page from [sentry 1.x to sentry 4.x](https://docs.sentry.io/platforms/java/migration/).

Releases from sentry-android [2.x and its changelogs](https://github.com/getsentry/sentry-android/releases).

Sentry Android Gradle Plugin repo [sits on another repo](https://github.com/getsentry/sentry-android-gradle-plugin)

# Blog posts

[Mobile Vitals - Four Metrics Every Mobile Developer Should Care About](https://blog.sentry.io/2021/08/23/mobile-vitals-four-metrics-every-mobile-developer-should-care-about/).

[Supporting Native Android Libraries Loaded From APKs](https://blog.sentry.io/2021/05/13/supporting-native-android-libraries-loaded-from-apks).

[A Sanity Listicle for Mobile Developers](https://blog.sentry.io/2021/03/30/a-sanity-listicle-for-mobile-developers/).

[Performance Monitoring for Android Applications](https://blog.sentry.io/2021/03/18/performance-monitoring-for-android-applications).

[Close the Loop with User Feedback](https://blog.sentry.io/2021/02/16/close-the-loop-with-user-feedback).

[How to use Sentry Attachments with Mobile Applications](https://blog.sentry.io/2021/02/03/how-to-use-sentry-attachments-with-mobile-applications).

[Adding Native support to our Android SDK](https://blog.sentry.io/2019/11/25/adding-native-support-to-our-android-sdk).

[New Android SDK How-to](https://blog.sentry.io/2019/12/10/new-android-sdk-how-to).

# Samples

[Sample App. with Sentry Android SDK and Sentry Gradle Plugin](https://github.com/getsentry/examples/tree/master/android).

[Sample App. with Sentry Java SDK](https://github.com/getsentry/examples/tree/master/java).

[Sample for Development](https://github.com/getsentry/sentry-java/tree/main/sentry-samples).

# Development

This repository includes [`sentry-native`](https://github.com/getsentry/sentry-native/) as a git submodule.
To build against `sentry-native` checked-out elsewhere in your file system, create a symlink `sentry-android-ndk/sentry-native-local` that points to your `sentry-native` directory.
For example, if you had `sentry-native` checked-out in a sibling directory to this repo:

`ln -s ../../sentry-native sentry-android-ndk/sentry-native-local`

which will be picked up by `gradle` and used instead of the git submodule.
This directory is also included in `.gitignore` not to be shown as pending changes.

# Sentry Self Hosted Compatibility

Since version 3.0.0 of this SDK, Sentry version >= v20.6.0 is required. This only applies to on-premise Sentry, if you are using [sentry.io](http://sentry.io/) no action is needed.

# Resources

* [![Java Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg?label=java%20docs)](https://docs.sentry.io/platforms/java/)
* [![Android Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg?label=android%20docs)](https://docs.sentry.io/platforms/android/)
* [![Forum](https://img.shields.io/badge/forum-sentry-green.svg)](https://forum.sentry.io/c/sdks)
* [![Discord Chat](https://img.shields.io/discord/621778831602221064?logo=discord&logoColor=ffffff&color=7389D8)](https://discord.gg/PXa5Apfe7K)  
* [![Stack Overflow](https://img.shields.io/badge/stack%20overflow-sentry-green.svg)](http://stackoverflow.com/questions/tagged/sentry)
* [![Code of Conduct](https://img.shields.io/badge/code%20of%20conduct-sentry-green.svg)](https://github.com/getsentry/.github/blob/master/CODE_OF_CONDUCT.md)
* [![Twitter Follow](https://img.shields.io/twitter/follow/getsentry?label=getsentry&style=social)](https://twitter.com/intent/follow?screen_name=getsentry)
