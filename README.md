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
[![Discord Chat](https://img.shields.io/discord/621778831602221064.svg)](https://discordapp.com/channels/621778831602221064/621783562739384331)  

|      Packages          | bintray |
| ---------------------- | ------- |
| sentry-android | [![sentry-android](https://img.shields.io/bintray/v/getsentry/sentry-android/sentry-android)](https://bintray.com/getsentry/sentry-android/sentry-android?tab=overview) |
| sentry-android-core | [![sentry-android-core](https://img.shields.io/bintray/v/getsentry/sentry-android/sentry-android-core)](https://bintray.com/getsentry/sentry-android/sentry-android-core?tab=overview) |
| sentry-android-ndk | [![sentry-android-ndk](https://img.shields.io/bintray/v/getsentry/sentry-android/sentry-android-ndk)](https://bintray.com/getsentry/sentry-android/sentry-android-ndk?tab=overview) |
| sentry-core | [![sentry-core](https://img.shields.io/bintray/v/getsentry/sentry-android/sentry-core)](https://bintray.com/getsentry/sentry-android/sentry-core?tab=overview) |

# Docs

That's the initial page of the release [2.x and its docs](https://docs.sentry.io/platforms/android).

# Note

This SDK is under development and will be published as version 2.0 which will be released by the end of 2019. It includes many new features including NDK support.

Sentry has been offering an official SDK for Android for years now. If you are looking for the stable, LTS support of Sentry, please refer to the [1.x and its docs](https://docs.sentry.io/clients/java/integrations/#android).

# Development

This repository includes [`sentry-native`](https://github.com/getsentry/sentry-native/) as a git submodule.
To build against `sentry-native` checked-out elsewhere in your file system, create a symlink `sentry-android-ndk/sentry-native-local` that points to your `sentry-native` directory.
For example, if you had `sentry-native` checked-out in a sibling directory to this repo:

`ln -s ../../sentry-native sentry-android-ndk/sentry-native-local`

which will be picked up by `gradle` and used instead of the git submodule.
This directory is also included in `.gitignore` not to be shown as pending changes.
