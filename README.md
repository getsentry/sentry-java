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
[![X Follow](https://img.shields.io/twitter/follow/sentry?label=sentry&style=social)](https://x.com/intent/follow?screen_name=sentry)
[![Discord Chat](https://img.shields.io/discord/621778831602221064?logo=discord&logoColor=ffffff&color=7389D8)](https://discord.gg/PXa5Apfe7K)

| Packages                                | Maven Central                                                                                                                                                  | Minimum Android API Version |
|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------| ------- |
| sentry-android                          | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android?style=for-the-badge&logo=sentry&color=green)                          | 21 |
| sentry-android-core                     | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-core?style=for-the-badge&logo=sentry&color=green)                     | 21 |
| sentry-android-distribution             | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-distribution?style=for-the-badge&logo=sentry&color=green)             | 21 |
| sentry-android-ndk                      | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-ndk?style=for-the-badge&logo=sentry&color=green)                      | 21 |
| sentry-android-timber                   | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-timber?style=for-the-badge&logo=sentry&color=green)                   | 21 |
| sentry-android-fragment                 | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-fragment?style=for-the-badge&logo=sentry&color=green)                 | 21 |
| sentry-android-navigation               | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-navigation?style=for-the-badge&logo=sentry&color=green)               | 21 |
| sentry-android-sqlite                   | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-sqlite?style=for-the-badge&logo=sentry&color=green)                   | 21 |
| sentry-android-replay                   | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-android-replay?style=for-the-badge&logo=sentry&color=green)                   | 26 |
| sentry-compose-android                  | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-compose-android?style=for-the-badge&logo=sentry&color=green)                  | 21 |
| sentry-compose-desktop                  | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-compose-desktop?style=for-the-badge&logo=sentry&color=green)                  | 
| sentry-compose                          | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-compose?style=for-the-badge&logo=sentry&color=green)                          | 
| sentry-apache-http-client-5             | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-apache-http-client-5?style=for-the-badge&logo=sentry&color=green)             |
| sentry                                  | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry?style=for-the-badge&logo=sentry&color=green)                                  | 21 |
| sentry-jul                              | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-jul?style=for-the-badge&logo=sentry&color=green)                              |
| sentry-jdbc                             | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-jdbc?style=for-the-badge&logo=sentry&color=green)                             |
| sentry-apollo                           | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-apollo?style=for-the-badge&logo=sentry&color=green)                           | 21 |
| sentry-apollo-3                         | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-apollo-3?style=for-the-badge&logo=sentry&color=green)                         | 21 |
| sentry-apollo-4                         | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-apollo-4?style=for-the-badge&logo=sentry&color=green)                         | 21 |
| sentry-kotlin-extensions                | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-kotlin-extensions?style=for-the-badge&logo=sentry&color=green)                | 21 |
| sentry-ktor-client                      | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-ktor-client?style=for-the-badge&logo=sentry&color=green)                      | 21 |
| sentry-servlet                          | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-servlet?style=for-the-badge&logo=sentry&color=green)                          | |
| sentry-servlet-jakarta                  | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-servlet-jakarta?style=for-the-badge&logo=sentry&color=green)                  | |
| sentry-spring-boot                      | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-boot?style=for-the-badge&logo=sentry&color=green)                      |
| sentry-spring-boot-jakarta              | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-boot-jakarta?style=for-the-badge&logo=sentry&color=green)              |
| sentry-spring-boot-4                    | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-boot-4?style=for-the-badge&logo=sentry&color=green)                    |
| sentry-spring-boot-4-starter            | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-boot-4-starter?style=for-the-badge&logo=sentry&color=green)            |
| sentry-spring-boot-starter              | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-boot-starter?style=for-the-badge&logo=sentry&color=green)              |
| sentry-spring-boot-starter-jakarta      | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-boot-starter-jakarta?style=for-the-badge&logo=sentry&color=green)      |
| sentry-spring                           | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring?style=for-the-badge&logo=sentry&color=green)                           |
| sentry-spring-jakarta                   | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-jakarta?style=for-the-badge&logo=sentry&color=green)                   |
| sentry-spring-7                         | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spring-7?style=for-the-badge&logo=sentry&color=green)                         |
| sentry-logback                          | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-logback?style=for-the-badge&logo=sentry&color=green)                          |
| sentry-log4j2                           | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-log4j2?style=for-the-badge&logo=sentry&color=green)                           |
| sentry-bom                              | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-bom?style=for-the-badge&logo=sentry&color=green)                              |
| sentry-graphql                          | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-graphql?style=for-the-badge&logo=sentry&color=green)                          |
| sentry-graphql-core                     | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-graphql-core?style=for-the-badge&logo=sentry&color=green)                     |
| sentry-graphql-22                       | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-graphql-22?style=for-the-badge&logo=sentry&color=green)                       |
| sentry-quartz                           | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-quartz?style=for-the-badge&logo=sentry&color=green)                           |
| sentry-openfeign                        | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-openfeign?style=for-the-badge&logo=sentry&color=green)                        |
| sentry-openfeature                      | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-openfeature?style=for-the-badge&logo=sentry&color=green)                      |
| sentry-launchdarkly-android             | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-launchdarkly-android?style=for-the-badge&logo=sentry&color=green)             |
| sentry-launchdarkly-server              | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-launchdarkly-server?style=for-the-badge&logo=sentry&color=green)              |
| sentry-opentelemetry-agent              | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-opentelemetry-agent?style=for-the-badge&logo=sentry&color=green)              |
| sentry-opentelemetry-agentcustomization | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-opentelemetry-agentcustomization?style=for-the-badge&logo=sentry&color=green) |
| sentry-opentelemetry-core               | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-opentelemetry-core?style=for-the-badge&logo=sentry&color=green)               |
| sentry-okhttp                           | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-okhttp?style=for-the-badge&logo=sentry&color=green)                           |
| sentry-reactor                          | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-reactor?style=for-the-badge&logo=sentry&color=green)                          |
| sentry-spotlight                        | ![Maven Central Version](https://img.shields.io/maven-central/v/io.sentry/sentry-spotlight?style=for-the-badge&logo=sentry&color=green)                        |

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
