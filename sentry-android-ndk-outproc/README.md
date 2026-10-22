# sentry-android-ndk-outproc

Experimental opt-in alternative to `sentry-android-ndk`, with an Android service
that runs the native crash daemon. Depends on `sentry-native-ndk-outproc`. Select
it through the Sentry Gradle plugin:

```kotlin
sentry {
    ndkBackend.set("native")
}
```

The default is the inproc backend.

The crash service needs API 30+. On older versions, native crash reporting is
disabled.
