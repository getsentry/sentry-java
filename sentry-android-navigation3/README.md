# sentry-android-navigation3

[Navigation 3](https://developer.android.com/guide/navigation/navigation-3) instrumentation for the Sentry Android SDK.

Use `SentryNavEffect` alongside your `NavDisplay` to record navigation transactions, breadcrumbs, screen names, and back stack context as
users move around your app. Configure the data you want captured via `SentryNavOptions`.

`SentryNavEffect` uses a `BackStackEntryMapper` to turn each Nav3 back stack key into a screen name and arguments. Choose one of these
approaches:

| Mapper                                           | What Sentry records                           |
|--------------------------------------------------|-----------------------------------------------|
| `BackStackEntryMapper.serialName()`              | The serialization name; no arguments          |
| `BackStackEntryMapper.serialNameWithArguments()` | The serialization name and every key property |
| A custom `BackStackEntryMapper`                  | The names and arguments you choose            |

For the two built-in mappers, an `@Serializable` key's name is its fully qualified class name (for example,
`/com.example.Route.Detail`) or its `@SerialName` value. Use `@SerialName` for shorter names. A key that isn't `@Serializable` is
recorded as `/unknown`.

`serialNameWithArguments()` sends the full content of each key, which may include personal data. Use it only for keys that are safe to
send to Sentry. Values whose property names match the sensitive denylist, such as `token`, `password`, or `session`, become
`"[Filtered]"`. To send only selected properties or choose different names, use a custom mapper and delegate other keys to
`serialName()`:

```kotlin
val mapper = BackStackEntryMapper<NavKey> { key ->
  when (key) {
    is ProductDetail -> SentryBackStackEntry("ProductDetail", mapOf("product_id" to key.productId))
    else -> BackStackEntryMapper.serialName().map(key)
  }
}
```

If you call `NavDisplay(entries, ...)` or `NavDisplay(sceneState, ...)`, pass `SentryNavEffect` the back stack your entries are built
from, not the entries themselves:

```kotlin
val backStack = rememberNavBackStack(Home)
SentryNavEffect(backStack, BackStackEntryMapper.serialName())

val entries = rememberDecoratedNavEntries(backStack, decorators, entryProvider)
NavDisplay(entries = entries, ...)
```

Otherwise, all of your destinations will be recorded as `/unknown`. (`NavEntry` doesn't expose its key, so names can't be derived from a
list of entries.)

See the [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/) for additional details, including
configuration, supported setups, and limitations.
