<p align="center">
  <a href="https://sentry.io/?utm_source=github&utm_medium=logo" target="_blank">
    <picture>
      <source srcset="https://sentry-brand.storage.googleapis.com/sentry-logo-white.png" media="(prefers-color-scheme: dark)" />
      <source srcset="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" media="(prefers-color-scheme: light), (prefers-color-scheme: no-preference)" />
      <img src="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" alt="Sentry" width="280">
    </picture>
  </a>
</p>

_Bad software is everywhere, and we're tired of it. Sentry is on a mission to help developers write better software faster, so we can get back to enjoying technology. If you want to join us [<kbd>**Check out our open positions**</kbd>](https://sentry.io/careers/)_

Sentry SDK for Java and Android
===========
[![GH Workflow](https://img.shields.io/github/actions/workflow/status/getsentry/sentry-java/build.yml?branch=main)](https://github.com/getsentry/sentry-java/actions)
[![codecov](https://codecov.io/gh/getsentry/sentry-java/branch/main/graph/badge.svg)](https://codecov.io/gh/getsentry/sentry-java)
[![Discord Chat](https://img.shields.io/discord/621778831602221064?logo=discord&logoColor=ffffff&color=7389D8)](https://discord.gg/PXa5Apfe7K)

| Packages                                | Maven Central                                                                                                                                                                                                                        | Minimum Android API Version |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| ------- |
| sentry-android                          | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android)                                                   | 21 |
| sentry-android-core                     | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-core)                                         | 21 |
| sentry-android-ndk                      | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-ndk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-ndk)                                           | 21 |
| sentry-android-timber                   | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-timber/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-timber)                                     | 21 |
| sentry-android-fragment                 | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-fragment/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-fragment)                                 | 21 |
| sentry-android-navigation               | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-navigation/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-navigation)                             | 21 |
| sentry-android-sqlite                   | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-sqlite/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-sqlite)                                     | 21 |
| sentry-android-replay                   | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-replay/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-android-replay)                                     | 26 |
| sentry-compose-android                  | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-compose-android/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-compose-android)                                   | 21 |
| sentry-compose-desktop                  | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-compose-desktop/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-compose-desktop)                                   | 
| sentry-compose                          | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-compose/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-compose)                                                   | 
| sentry-apache-http-client-5             | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apache-http-client-5/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apache-http-client-5)                         |
| sentry                                  | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry)                                                                   | 21 |
| sentry-jul                              | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-jul/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-jul)                                                           |
| sentry-jdbc                             | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-jdbc/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-jdbc)                                                         |
| sentry-apollo                           | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apollo/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apollo)                                                     | 21 |
| sentry-apollo-3                         | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apollo-3/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-apollo-3)                                                 | 21 |
| sentry-kotlin-extensions                | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-kotlin-extensions/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-kotlin-extensions)                               | 21 |
| sentry-servlet                          | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-servlet/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-servlet)                                                   | |
| sentry-servlet-jakarta                  | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-servlet-jakarta/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-servlet-jakarta)                                   | |
| sentry-spring-boot                      | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot)                                           |
| sentry-spring-boot-jakarta              | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-jakarta/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-jakarta)                           |
| sentry-spring-boot-starter              | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-starter/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-starter)                           |
| sentry-spring-boot-starter-jakarta      | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-starter-jakarta/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-boot-starter-jakarta)           |
| sentry-spring                           | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring)                                                     |
| sentry-spring-jakarta                   | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-jakarta/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-spring-jakarta)                                     |
| sentry-logback                          | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-logback/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-logback)                                                   |
| sentry-log4j2                           | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-log4j2/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-log4j2)                                                     |
| sentry-bom                              | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-bom/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-bom)                                                           |
| sentry-graphql                          | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-graphql/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-graphql)                                                   |
| sentry-graphql-core                     | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-graphql-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-graphql-core)                                         |
| sentry-graphql-22                       | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-graphql-22/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-graphql-22)                                             |
| sentry-quartz                           | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-quartz/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-quartz)                                                     |
| sentry-openfeign                        | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-openfeign/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-openfeign)                                               |
| sentry-opentelemetry-agent              | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-opentelemetry-agent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-opentelemetry-agent)                           |
| sentry-opentelemetry-agentcustomization | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-opentelemetry-agentcustomization/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-opentelemetry-agentcustomization) |
| sentry-opentelemetry-core               | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-opentelemetry-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-opentelemetry-core)                             |
| sentry-okhttp                           | [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-okhttp/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.sentry/sentry-okhttp)                                                     |

# Releases

This repo uses the following ways to release SDK updates:

- `Pre-release`: We create pre-releases (alpha, beta, RC,…) for larger and potentially more impactful changes, such as new features or major versions.
- `Latest`: We continuously release major/minor/hotfix versions from the `main` branch. These releases go through all our internal quality gates and are very safe to use and intended to be the default for most teams.
- `Stable`: We promote releases from `Latest` when they have been used in the field for some time and in scale, considering time since release, adoption, and other quality and stability metrics. These releases will be indicated on the releases page (https://github.com/getsentry/sentry-java/releases/) with the `Stable` suffix.

# Useful links and docs

* A deep dive into how we built [Session Replay for Android](https://www.droidcon.com/2024/11/22/rewind-and-resolve-a-deep-dive-into-building-session-replay-for-android/) at Droidcon London 2024.
* Current Javadocs [generated from source code](https://getsentry.github.io/sentry-java/).
* Java SDK version 1.x [can still be found here](https://docs.sentry.io/clients/java/).
* Migration page from [sentry-android 1.x and 2.x to sentry-android 4.x](https://docs.sentry.io/platforms/android/migration/).
* Migration page from [sentry 1.x to sentry 4.x](https://docs.sentry.io/platforms/java/migration/).
* Releases from sentry-android [2.x and its changelogs](https://github.com/getsentry/sentry-android/releases).
* Sentry Android Gradle Plugin repo [sits on another repo](https://github.com/getsentry/sentry-android-gradle-plugin)

# Blog posts

* [Sentry’s Android Gradle Plugin Updated with Room Support and More](https://blog.sentry.io/2022/04/20/sentrys-android-gradle-plugin-updated-with-room-support-and-more/)
* [Troubleshooting Spring Boot applications with Sentry](https://blog.sentry.io/2022/04/18/troubleshooting-spring-boot-applications-with-sentry)
* [Android Manifest Placeholders](https://blog.sentry.io/2022/03/30/android-manifest-placeholders/)
* [UI Breadcrumbs for Android Error Events](https://blog.sentry.io/2022/02/08/ui-breadcrumbs-for-android-error-events)
* [Bytecode transformations: The Android Gradle Plugin](https://blog.sentry.io/2021/12/14/bytecode-transformations-the-android-gradle-plugin)
* [Sentry's response to Log4j vulnerability CVE-2021-44228](https://blog.sentry.io/2021/12/15/sentrys-response-to-log4j-vulnerability-cve-2021-44228)
* [Mobile Vitals - Four Metrics Every Mobile Developer Should Care About](https://blog.sentry.io/2021/08/23/mobile-vitals-four-metrics-every-mobile-developer-should-care-about/).
* [Supporting Native Android Libraries Loaded From APKs](https://blog.sentry.io/2021/05/13/supporting-native-android-libraries-loaded-from-apks).
* [A Sanity Listicle for Mobile Developers](https://blog.sentry.io/2021/03/30/a-sanity-listicle-for-mobile-developers/).
* [Performance Monitoring for Android Applications](https://blog.sentry.io/2021/03/18/performance-monitoring-for-android-applications).
* [Close the Loop with User Feedback](https://blog.sentry.io/2021/02/16/close-the-loop-with-user-feedback).
* [How to use Sentry Attachments with Mobile Applications](https://blog.sentry.io/2021/02/03/how-to-use-sentry-attachments-with-mobile-applications).
* [Adding Native support to our Android SDK](https://blog.sentry.io/2019/11/25/adding-native-support-to-our-android-sdk).
* [New Android SDK How-to](https://blog.sentry.io/2019/12/10/new-android-sdk-how-to).

# Samples

* [Sample App. with Sentry Android SDK and Sentry Gradle Plugin](https://github.com/getsentry/examples/tree/master/android).
* [Sample App. with Sentry Java SDK](https://github.com/getsentry/examples/tree/master/java).
* [Sample for Development](https://github.com/getsentry/sentry-java/tree/main/sentry-samples).

# Development

This repository includes [`sentry-native`](https://github.com/getsentry/sentry-native/) as a git submodule.
To build against `sentry-native` checked-out elsewhere in your file system, create a symlink `sentry-android-ndk/sentry-native-local` that points to your `sentry-native` directory.
For example, if you had `sentry-native` checked-out in a sibling directory to this repo:

`ln -s ../../sentry-native sentry-android-ndk/sentry-native-local`

which will be picked up by `gradle` and used instead of the git submodule.
This directory is also included in `.gitignore` not to be shown as pending changes.

# Sentry Self Hosted Compatibility

Since version 3.0.0 of this SDK, Sentry version >= v20.6.0 is required. This only applies to self-hosted Sentry, if you are using [sentry.io](http://sentry.io/) no action is needed.

Since version 6.0.0 of this SDK, Sentry version >= v21.9.0 is required or you have to manually disable sending client reports via the `sendClientReports` option. This only applies to self-hosted Sentry, if you are using [sentry.io](http://sentry.io/) no action is needed.

Since version 7.0.0 of this SDK, Sentry version >= 22.12.0 is required to properly ingest transactions with unfinished spans. This only applies to self-hosted Sentry, if you are using [sentry.io](http://sentry.io/) no action is needed.

# Resources

* [![Java Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg?label=java%20docs)](https://docs.sentry.io/platforms/java/)
* [![Android Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg?label=android%20docs)](https://docs.sentry.io/platforms/android/)
* [![Discussions](https://img.shields.io/github/discussions/getsentry/sentry-java.svg)](https://github.com/getsentry/sentry-java/discussions)
* [![Discord Chat](https://img.shields.io/discord/621778831602221064?logo=discord&logoColor=ffffff&color=7389D8)](https://discord.gg/PXa5Apfe7K)
* [![Stack Overflow](https://img.shields.io/badge/stack%20overflow-sentry-green.svg)](http://stackoverflow.com/questions/tagged/sentry)
* [![Code of Conduct](https://img.shields.io/badge/code%20of%20conduct-sentry-green.svg)](https://github.com/getsentry/.github/blob/master/CODE_OF_CONDUCT.md)
* [![Twitter Follow](https://img.shields.io/twitter/follow/getsentry?label=getsentry&style=social)](https://twitter.com/intent/follow?screen_name=getsentry)
