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

# Development

This repository includes [`sentry-native`](https://github.com/getsentry/sentry-native/) as a git submodule.
To build against `sentry-native` checked-out elsewhere in your file system, create a symlink `sentry-android-ndk/sentry-native-local` that points to your `sentry-native` directory.
For example, if you had `sentry-native` checked-out in a sibling directory to this repo:

`ln -s ../../sentry-native sentry-android-ndk/sentry-native-local`

which will be picked up by `gradle` and used instead of the git submodule.
This directory is also included in `.gitignore` not to be shown as pending changes.
