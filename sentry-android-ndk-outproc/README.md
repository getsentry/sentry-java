# sentry-android-ndk-outproc

Experimental opt-in alternative to the inproc native backend, with an Android
service that runs the native crash daemon. Builds on `sentry-android-ndk` and
replaces its `sentry-native-ndk` with `sentry-native-ndk-outproc`.

The default is the inproc backend.

To select it, add `sentry-android-ndk-outproc`. With the `sentry-android`
bundle, which brings `sentry-native-ndk`, also exclude it:

```kotlin
configurations.configureEach {
    exclude(group = "io.sentry", module = "sentry-native-ndk")
}
```

Otherwise, the build fails because it contains both native backends.

The crash service needs API 30+. On older versions, native crash reporting is
disabled.
