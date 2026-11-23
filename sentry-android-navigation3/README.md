# sentry-android-navigation3

[Navigation 3](https://developer.android.com/guide/navigation/navigation-3) instrumentation for the Sentry Android SDK.

Use `SentryNavEffect` alongside your `NavDisplay` to record navigation transactions, breadcrumbs, screen names, and back stack context as
users move around your app. Configure the data you want captured via `SentryNavOptions`.

Because Nav3 back stacks can have arbitrary key types, host apps need to provide `SentryNavEffect` with a mapping between their back stack
keys and the data Sentry should display for each (see `BackStackEntryMapper` and the built-in `SerialName` and
`SerialNameAndProperties` mappers).

## Auto-instrumentation (experimental)

With the [Sentry Android Gradle Plugin](https://docs.sentry.io/platforms/android/configuration/gradle/), you can record the same data
without calling `SentryNavEffect`:

```kotlin
sentry {
  tracingInstrumentation {
    navigation3 { enabled = true }
  }
}
```

The plugin modifies `NavDisplay` inside `androidx.navigation3:navigation3-ui` so that every `NavDisplay(backStack = ...)` observes its back
stack and names entries with `BackStackEntryMapper.SerialName`. Arguments aren't recorded. `NavDisplay(entries = ...)` and
`NavDisplay(sceneState = ...)` aren't covered; call `SentryNavEffect` for those, passing it the back stack the entries are built from.

If a screen has more than one `NavDisplay`, only one of them records:

- a `SentryNavEffect` you call yourself takes priority over the auto-instrumented `NavDisplay` it observes, and over any other
  auto-instrumented `NavDisplay` in the same Activity or Fragment;
- otherwise, the first auto-instrumented `NavDisplay` in an Activity or Fragment records, which is usually the outermost one.

Call your own `SentryNavEffect` before its `NavDisplay`. If it's called after, the first screen is recorded twice.

See the [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/) for additional details, including
configuration, supported setups, and limitations.
