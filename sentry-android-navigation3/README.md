# sentry-android-navigation3

[Navigation 3](https://developer.android.com/guide/navigation/navigation-3) instrumentation for the Sentry Android SDK.

Use `SentryNavEffect` alongside your `NavDisplay` to record navigation transactions, breadcrumbs, screen names, and back stack context as
users move around your app. Configure the data you want captured via `SentryNavOptions`.

Because Nav3 back stacks can have arbitrary key types, `SentryNavEffect` uses a `BackStackEntryMapper` to decide the name and arguments
Sentry displays for each back stack key. Pass `BackStackEntryMapper.serialName()` to name `@Serializable` keys after their
kotlinx.serialization serial names, without arguments. A serial name is the key class's fully qualified name (e.g.
`/com.example.Route.Detail`), or its `@SerialName` value if it has one; use `@SerialName` for shorter names. Keys that aren't
`@Serializable` are recorded as `/unknown`.

Provide your own `BackStackEntryMapper` to choose different names or to add arguments. It can delegate the keys it doesn't handle to
`BackStackEntryMapper.serialName()`:

```kotlin
val mapper = BackStackEntryMapper<NavKey> { key ->
  when (key) {
    is ProductDetail -> SentryBackStackEntry("ProductDetail", mapOf("product_id" to key.productId))
    else -> BackStackEntryMapper.serialName().map(key)
  }
}
```

If you call `NavDisplay(entries, ...)` or `NavDisplay(sceneState, ...)`, pass `SentryNavEffect` the back stack your entries are built
from, not the entries themselves. `NavEntry` doesn't expose its key, so a list of entries can't be named:

```kotlin
val backStack = rememberNavBackStack(Home)
SentryNavEffect(backStack, BackStackEntryMapper.serialName())

val entries = rememberDecoratedNavEntries(backStack, decorators, entryProvider)
NavDisplay(entries = entries, ...)
```

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
stack and names entries with `BackStackEntryMapper.serialName()`. Arguments aren't recorded. `NavDisplay(entries = ...)` and
`NavDisplay(sceneState = ...)` aren't covered; call `SentryNavEffect` for those.

If a screen has more than one `NavDisplay`, only one of them records:

- a `SentryNavEffect` you call yourself takes priority over the auto-instrumented `NavDisplay` it observes, and over any other
  auto-instrumented `NavDisplay` in the same Activity or Fragment;
- otherwise, the first auto-instrumented `NavDisplay` in an Activity or Fragment records, which is usually the outermost one.

Call your own `SentryNavEffect` before its `NavDisplay`, as described above. If it's called after, the first screen is recorded twice.

See the [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/) for additional details, including
configuration, supported setups, and limitations.
