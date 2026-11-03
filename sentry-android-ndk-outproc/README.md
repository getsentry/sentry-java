# sentry-android-ndk-outproc

Experimental opt-in alternative to the inproc native backend, with an Android
service that runs the native crash daemon. Builds on `sentry-android-ndk` and
replaces its `sentry-native-ndk` with `sentry-native-ndk-outproc`. Select it
through the Sentry Gradle plugin:

```kotlin
sentry {
    ndkBackend.set("native")
}
```

The default is the inproc backend.

Without the plugin, add `sentry-android-ndk-outproc`. With the `sentry-android`
bundle, which brings `sentry-native-ndk`, also exclude it:

```kotlin
configurations.configureEach {
    exclude(group = "io.sentry", module = "sentry-native-ndk")
}
```

Otherwise, the build fails because it contains both native backends.

The crash service needs API 30+. On older versions, native crash reporting is
disabled.
