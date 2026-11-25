# sentry-android-navigation3

[Navigation 3](https://developer.android.com/guide/navigation/navigation-3) instrumentation for the Sentry Android SDK.

Use `SentryNavEffect` alongside your `NavDisplay` to record navigation transactions, breadcrumbs, screen names, and back stack context as
users move around your app. Configure the data you want captured via `SentryNavOptions`.

This module requires Nav3. It doesn't declare its own dependencies on Compose, lifecycle, or kotlinx.serialization, but uses the versions
your Nav3 dependency brings in.

Because Nav3 back stacks can have arbitrary key types, host apps need to provide `SentryNavEffect` with a mapping between their back stack
keys and the data Sentry should display for each (see `BackStackEntryMapper` and the built-in `SerialName` mapper).

See the [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/) for additional details, including
configuration, supported setups, and limitations.
