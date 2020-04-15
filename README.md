<p align="center">
  <a href="https://sentry.io" target="_blank" align="center">
    <img src="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" width="280">
  </a>
  <br />
</p>

Android SDK for Sentry
===========
[![Travis](https://travis-ci.com/getsentry/sentry-android.svg?branch=master)](https://travis-ci.com/getsentry/sentry-android)
[![AppVeyor](https://ci.appveyor.com/api/projects/status/kr49snupeb1dsgwa/branch/master?svg=true)](https://ci.appveyor.com/project/sentry/sentry-android/branch/master)
[![Tests](https://img.shields.io/appveyor/tests/sentry/sentry-android/master?compact_message)](https://ci.appveyor.com/project/sentry/sentry-android/branch/master/tests)
[![codecov](https://codecov.io/gh/getsentry/sentry-android/branch/master/graph/badge.svg)](https://codecov.io/gh/getsentry/sentry-android)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/getsentry/sentry-android.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/getsentry/sentry-android/context:java)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/getsentry/sentry-android.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/getsentry/sentry-android/alerts/)

|      Packages          | bintray |
| ---------------------- | ------- |
| sentry-android | [![sentry-android](https://img.shields.io/bintray/v/getsentry/sentry-android/io.sentry:sentry-android)](https://bintray.com/getsentry/sentry-android/io.sentry:sentry-android?tab=overview) |
| sentry-android-core | [![sentry-android-core](https://img.shields.io/bintray/v/getsentry/sentry-android/io.sentry:sentry-android-core)](https://bintray.com/getsentry/sentry-android/io.sentry:sentry-android-core?tab=overview) |
| sentry-android-ndk | [![sentry-android-ndk](https://img.shields.io/bintray/v/getsentry/sentry-android/io.sentry:sentry-android-ndk)](https://bintray.com/getsentry/sentry-android/io.sentry:sentry-android-ndk?tab=overview) |
| sentry-core | [![sentry-core](https://img.shields.io/bintray/v/getsentry/sentry-android/io.sentry:sentry-core)](https://bintray.com/getsentry/sentry-android/io.sentry:sentry-core?tab=overview) |

# Docs

That's the initial page of the release [2.x and its docs](https://docs.sentry.io/platforms/android).

Migration page from [sentry-android 1.x to sentry-android 2.0](https://docs.sentry.io/platforms/android/migrate).

# Blog posts

[New Android SDK How-to](https://blog.sentry.io/2019/12/10/new-android-sdk-how-to).

[Adding Native support to our Android SDK](https://blog.sentry.io/2019/11/25/adding-native-support-to-our-android-sdk).

# Samples

[Sample App. with Sentry Android SDK and Sentry Gradle Plugin](https://github.com/getsentry/examples/tree/master/android).

[Sample for Development](https://github.com/getsentry/sentry-android/tree/master/sentry-sample).

# Development

This repository includes [`sentry-native`](https://github.com/getsentry/sentry-native/) as a git submodule.
To build against `sentry-native` checked-out elsewhere in your file system, create a symlink `sentry-android-ndk/sentry-native-local` that points to your `sentry-native` directory.
For example, if you had `sentry-native` checked-out in a sibling directory to this repo:

`ln -s ../../sentry-native sentry-android-ndk/sentry-native-local`

which will be picked up by `gradle` and used instead of the git submodule.
This directory is also included in `.gitignore` not to be shown as pending changes.

# Resources

* [![Documentation](https://img.shields.io/badge/documentation-sentry.io-green.svg)](https://docs.sentry.io/platforms/android/)
* [![Forum](https://img.shields.io/badge/forum-sentry-green.svg)](https://forum.sentry.io/c/sdks)
* [![Discord](https://img.shields.io/discord/621778831602221064)](https://discord.gg/Ww9hbqr)
* [![Stack Overflow](https://img.shields.io/badge/stack%20overflow-sentry-green.svg)](http://stackoverflow.com/questions/tagged/sentry)
* [![Twitter Follow](https://img.shields.io/twitter/follow/getsentry?label=getsentry&style=social)](https://twitter.com/intent/follow?screen_name=getsentry)
