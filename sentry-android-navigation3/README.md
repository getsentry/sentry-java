# sentry-android-navigation3

This module provides Sentry instrumentation for single-stack Jetpack Navigation 3 apps on Android.

The integration observes an app-owned Navigation 3 back stack and records:

- Navigation breadcrumbs
- Navigation transactions, when tracing is enabled
- `scope.screen` and `contexts.app.view_names`
- `contexts.navigation.backstack` crash context

Use this module for apps with one active Navigation 3 back stack. Multiple retained back stacks,
visible-entry decorators, and multipane-aware primary-route selection are intentionally out of scope
for this integration.

## Installation

Add the Sentry Navigation 3 integration and your Navigation 3 dependencies to your app:

```kotlin
dependencies {
  implementation("io.sentry:sentry-android-navigation3:<sentry-version>")

  // Add the Navigation 3 artifacts used by your app.
  implementation("androidx.navigation3:navigation3-runtime:<navigation3-version>")
  implementation("androidx.navigation3:navigation3-ui:<navigation3-version>")
}
```

This module has a minimum Android API level of 23.

## Basic Usage

Create a Sentry decorator for the back stack you want to instrument, then pass it to Navigation 3's
`NavDisplay`.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.sentry.compose.navigation3.SentryNavBackStack
import io.sentry.compose.navigation3.rememberSentryNavEntryDecorator

data object Home
data class Profile(val userId: String)

@Composable
fun AppNavigation() {
  val backStack = remember { mutableStateListOf<Any>(Home) }
  val sentryDecorator =
    rememberSentryNavEntryDecorator(
      backStack = SentryNavBackStack(entries = backStack),
    )

  NavDisplay(
    backStack = backStack,
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        sentryDecorator,
      ),
    entryProvider =
      entryProvider {
        entry<Home> { HomeScreen() }
        entry<Profile> { route -> ProfileScreen(route.userId) }
      },
  )
}
```

With this setup, pushing `Profile("123")` after `Home` records a navigation breadcrumb similar to:

```text
type=breadcrumb category=navigation data.from=/Home data.to=/Profile
```

It also starts a navigation transaction named `/Profile` when tracing is enabled.

## Migration From Nav2

If you're moving from the Navigation 2 Compose integration
(`NavHostController.withSentryObservableEffect`) to `rememberSentryNavEntryDecorator`, the high-level signals are
similar, but the API surface, route model, and transaction timing are different.

### Entry point

Nav2 attaches to a `NavHostController`:

```kotlin
val navController = rememberNavController().withSentryObservableEffect()
```

Nav3 observes your app-owned back stack directly:

```kotlin
val backStack = remember { mutableStateListOf<Route>(Home) }
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
  )
NavDisplay(
  backStack = backStack,
  entryDecorators =
    listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      sentryDecorator,
    ),
  entryProvider = appEntryProvider,
)
```

This means Nav3 does not inspect `NavDestination` objects. It only sees the back-stack entries you place in
`backStack`.

### Signal toggles

Nav2 exposes:

- `enableNavigationBreadcrumbs`
- `enableNavigationTracing`

Nav3 exposes:

- `enableNavigationBreadcrumbs`
- `enableNavigationTransactions`
- `captureBackStack`

`enableNavigationTransactions` is the Nav3 equivalent of Nav2's `enableNavigationTracing`. In both integrations,
disabling it stops route-scoped navigation transactions from being created, but the SDK still advances the trace
context so work after a navigation does not remain attached to the previous route's trace.

`captureBackStack` is new in Nav3. Nav2 does not attach a serialized back-stack snapshot to
`contexts.navigation.backstack`.

### Route naming

Nav2 derives transaction and breadcrumb route names from `NavDestination.route`, with a resource-id fallback,
and strips route parameters from the transaction name.

Nav3 derives route names from your back-stack entry type:

- by default, the entry class simple name, such as `Profile` -> `/Profile`
- or a custom `nameExtractor`, if you provide one

If you want Nav3 events to keep the same stable route names you used with Nav2, add a `nameExtractor` that maps
your Nav3 entries onto those existing product route names.

### Route arguments

Nav2 automatically refines `Bundle` arguments from the destination change callback.

Nav3 does not inspect arguments automatically. To attach route arguments, provide `argumentsExtractor`.
Only return values that are safe to send to Sentry. Unlike Nav2's destination bundles, these values come
entirely from your app model, so you should explicitly redact any PII or secrets before returning them.

### Screen tracking

Both integrations continue to respect the SDK-wide `SentryOptions.isEnableScreenTracking` option.
`rememberSentryNavEntryDecorator` does not add a separate screen-tracking toggle.

### Current scope

Nav2 observes destination changes from a `NavController`. Nav3 currently observes a single app-owned back stack.
Multiple retained stacks, multipane visibility, and custom primary-route selection need a later API.

### Destination transaction timing

Navigation 2 notifies Sentry synchronously during `NavController.navigate()`, before Compose renders
the new destination. Navigation 3 exposes an app-owned back stack without an equivalent
pre-composition callback.

As a result, spans started during a destination's initial composable body are not guaranteed to
attach to the new route transaction. Start destination work from `LaunchedEffect`,
`DisposableEffect`, or `SideEffect` so it attaches to the new navigation transaction.

## Enable Navigation Transactions

`rememberSentryNavEntryDecorator` enables navigation transactions by default, but it only starts transactions
when SDK tracing is enabled in your Sentry options.

```kotlin
SentryAndroid.init(context) { options ->
  options.dsn = "https://public@example.com/project-id"
  options.tracesSampleRate = 1.0
}
```

Then create the instrumented decorator:

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
  )
```

To keep breadcrumbs and screen tracking but disable navigation transactions from this integration:

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
    options =
      SentryNavOptions().apply {
        enableNavigationTransactions = false
      },
  )
```

## Use Stable Route Names

By default, Sentry uses the back-stack key's class simple name as the route name. For example,
`Profile("123")` becomes `/Profile`.

Use `nameExtractor` when class names are not stable or when you want route names to match product
terminology.

```kotlin
sealed interface Route

data object Home : Route
data class Profile(val userId: String) : Route
data class Article(val slug: String) : Route

@Composable
fun AppNavigation() {
  val backStack = remember { mutableStateListOf<Route>(Home) }

  val sentryDecorator =
    rememberSentryNavEntryDecorator(
      backStack =
        SentryNavBackStack(entries = backStack).apply {
          nameExtractor = { route ->
            when (route) {
              Home -> "home"
              is Profile -> "profile"
              is Article -> "article"
            }
          }
        },
    )

  NavDisplay(
    backStack = backStack,
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        sentryDecorator,
      ),
    entryProvider = appEntryProvider,
  )
}
```

This records route names like `/home`, `/profile`, and `/article` instead of Kotlin class names.

If your extractor returns a leading slash, Sentry keeps one slash:

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack =
      SentryNavBackStack(entries = backStack).apply {
        nameExtractor = { route -> "/${route::class.simpleName}" }
      },
  )
```

## Add Route Arguments

Route arguments are disabled by default. To attach arguments, pass `argumentsExtractor`.

Only return values that are safe to send to Sentry. Argument values are attached to breadcrumbs,
transactions, and `contexts.navigation`. They are not controlled by `sendDefaultPii` and are not
automatically scrubbed by this integration.

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack =
      SentryNavBackStack(entries = backStack).apply {
        nameExtractor = { route ->
          when (route) {
            Home -> "home"
            is Profile -> "profile"
            is Article -> "article"
          }
        }
        argumentsExtractor = { route ->
          when (route) {
            Home -> emptyMap()
            is Profile -> mapOf("profile_type" to "member")
            is Article -> mapOf("slug" to route.slug)
          }
        }
      },
  )
```

The profile example deliberately avoids sending `userId`. If a route contains PII or secrets, redact
or replace those values before returning the map.

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack =
      SentryNavBackStack(entries = backStack).apply {
        argumentsExtractor = { route ->
          when (route) {
            is Profile -> mapOf("user_id" to "[Filtered]")
            else -> emptyMap()
          }
        }
      },
  )
```

Supported argument values are `String`, `Number`, `Boolean`, `null`, `Map`, and `Collection`. Other
values are converted with `toString()` and a warning is logged.

Nested safe values are supported:

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack =
      SentryNavBackStack(entries = backStack).apply {
        argumentsExtractor = { route ->
          when (route) {
            is Article ->
              mapOf(
                "article" to
                  mapOf(
                    "slug" to route.slug,
                    "tags" to listOf("android", "navigation"),
                  )
              )
            else -> emptyMap()
          }
        }
      },
  )
```

## Limit Back Stack Context

Sentry attaches the latest back-stack entries to `contexts.navigation.backstack`. The default limit
is 30 entries.

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
    options =
      SentryNavOptions().apply {
        maxCapturedBackStackEntries = 10
      },
  )
```

Set this based on how much navigation context is useful for debugging crashes and errors.

## Disable Specific Signals

You can disable individual parts of the integration.

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
    options =
      SentryNavOptions().apply {
        enableNavigationBreadcrumbs = false
      },
  )
```

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
    options =
      SentryNavOptions().apply {
        enableNavigationTransactions = false
      },
  )
```

```kotlin
val sentryDecorator =
  rememberSentryNavEntryDecorator(
    backStack = SentryNavBackStack(entries = backStack),
    options =
      SentryNavOptions().apply {
        captureBackStack = false
      },
  )
```

Screen tracking follows the SDK's `SentryOptions.isEnableScreenTracking` setting.

## Recommended App-Shell Setup

The display should live for the whole navigation session.

```kotlin
@Composable
fun RootScreen() {
  val backStack = remember { mutableStateListOf<Route>(Home) }

  val sentryDecorator =
    rememberSentryNavEntryDecorator(
      backStack =
        SentryNavBackStack(entries = backStack).apply {
          nameExtractor = { route ->
            when (route) {
              Home -> "home"
              is Profile -> "profile"
              is Article -> "article"
            }
          }
          argumentsExtractor = { route ->
            when (route) {
              Home -> emptyMap()
              is Profile -> mapOf("profile_type" to "member")
              is Article -> mapOf("slug" to route.slug)
            }
          }
        },
      options =
        SentryNavOptions().apply {
          maxCapturedBackStackEntries = 10
        },
    )

  NavDisplay(
    backStack = backStack,
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        sentryDecorator,
      ),
    entryProvider = appEntryProvider,
  )
}
```

`rememberSentryNavEntryDecorator` should remain in composition for the whole navigation session. Avoid
conditionally removing it while continuing to use the same back stack:

```kotlin
@Composable
fun ProfileScreen(backStack: SnapshotStateList<Route>) {
  // Do not create the app's Sentry navigation decorator inside a single destination screen.
  val sentryDecorator =
    rememberSentryNavEntryDecorator(
      backStack = SentryNavBackStack(entries = backStack),
    )
}
```

When `ProfileScreen` leaves composition, the decorator and its instrumentation are disposed.

## Current Limitations

This integration observes one back stack. It does not model multiple retained back stacks, tabbed
navigation stacks, multipane visibility, or a custom primary visible route. Those patterns need a
later API that accepts app-owned multi-stack state.

Dialog entries are treated like regular back-stack entries. If your app represents dialogs as route
keys, they can produce breadcrumbs and navigation transactions like any other route.
