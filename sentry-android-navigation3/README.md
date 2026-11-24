# sentry-android-navigation3

[Navigation 3](https://developer.android.com/guide/navigation/navigation-3) instrumentation for the Sentry Android SDK.

Use `SentryNavEffect` alongside your `NavDisplay` to record navigation transactions, breadcrumbs, screen names, and back stack context as
users move around your app. Configure the data you want captured via `SentryNavOptions`.

Because Nav3 back stacks can have arbitrary key types, host apps need to provide `SentryNavEffect` with a mapping between their back stack
keys and the data Sentry should display for each (see `BackStackEntryMapper` and the built-in `SerialName` and
`SerialNameAndProperties` mappers).

The built-in mappers take no configuration. To leave out or redact some of `SerialNameAndProperties`' arguments, wrap it in your own
mapper and return a changed copy of its result:

```kotlin
val mapper = BackStackEntryMapper<Any> { backStackEntry ->
  BackStackEntryMapper.SerialNameAndProperties.map(backStackEntry)?.let { entry ->
    SentryBackStackEntry(entry.name, entry.arguments?.filterKeys { it != "email" })
  }
}
```

This only changes top-level arguments, and left out values still count against the argument limits. See `SerialNameAndProperties`'
documentation for what it records, including the values it can't encode because it doesn't use your app's `SerializersModule`.

See the [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/) for additional details, including
configuration, supported setups, and limitations.
