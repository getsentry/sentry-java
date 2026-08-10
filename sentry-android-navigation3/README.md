// TODO ADAM: Add a section about navigation transaction / span policy. (See my own project notes.)
// TODO ADAM: Discuss all flags relevant to Nav3 Sentry data generation (eg, enableScreenTracking – which comes via Sentry Options; SentryNav3Options).

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

Call `SentryNav3Effect` from the composable that owns your app's Navigation 3 back stack.
Place it next to `NavDisplay` or at the same app-shell level. Do not call it from inside a single
screen destination, because it will leave composition when that screen is replaced.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import io.sentry.compose.navigation3.SentryNav3Effect

data object Home
data class Profile(val userId: String)

@Composable
fun AppNavigation() {
  val backStack = remember { mutableStateListOf<Any>(Home) }

  SentryNav3Effect(backStack = backStack)

  NavDisplay(
    backStack = backStack,
    // Configure your Navigation 3 entries here.
  )
}
```

With this setup, pushing `Profile("123")` after `Home` records a navigation breadcrumb similar to:

```text
type=breadcrumb category=navigation data.from=/Home data.to=/Profile
```

It also starts a navigation transaction named `/Profile` when tracing is enabled.

## Migration From Nav2

If you're moving from the Navigation 2 Compose integration (`NavHostController.withSentryObservableEffect`) to
`SentryNav3Effect`, the high-level signals are similar, but the API surface and route model are different.

### Entry point

Nav2 attaches to a `NavHostController`:

```kotlin
val navController = rememberNavController().withSentryObservableEffect()
```

Nav3 observes your app-owned back stack directly:

```kotlin
val backStack = remember { mutableStateListOf<Route>(Home) }
SentryNav3Effect(backStack = backStack)
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

Both integrations continue to respect the SDK-wide `SentryOptions.isEnableScreenTracking` option. The Nav3
effect does not add a separate screen-tracking toggle.

### Current scope

Nav2 observes destination changes from a `NavController`. Nav3 currently observes a single app-owned back stack.
Multiple retained stacks, multipane visibility, and custom primary-route selection need a later API.

## Enable Navigation Transactions

`SentryNav3Effect` enables navigation transactions by default, but it only starts transactions
when SDK tracing is enabled in your Sentry options.

```kotlin
SentryAndroid.init(context) { options ->
  options.dsn = "https://public@example.com/project-id"
  options.tracesSampleRate = 1.0
}
```

Then add the effect:

```kotlin
SentryNav3Effect(backStack = backStack)
```

To keep breadcrumbs and screen tracking but disable navigation transactions from this integration:

```kotlin
SentryNav3Effect(
  backStack = backStack,
  options =
    SentryNav3Options().apply {
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

  SentryNav3Effect(
    backStack = backStack,
    nameExtractor = { route ->
      when (route) {
        Home -> "home"
        is Profile -> "profile"
        is Article -> "article"
      }
    },
  )

  NavDisplay(backStack = backStack)
}
```

This records route names like `/home`, `/profile`, and `/article` instead of Kotlin class names.

If your extractor returns a leading slash, Sentry keeps one slash:

```kotlin
SentryNav3Effect(
  backStack = backStack,
  nameExtractor = { route -> "/${route::class.simpleName}" },
)
```

## Add Route Arguments

Route arguments are disabled by default. To attach arguments, pass `argumentsExtractor`.

Only return values that are safe to send to Sentry. Argument values are attached to breadcrumbs,
transactions, and `contexts.navigation`. They are not controlled by `sendDefaultPii` and are not
automatically scrubbed by this integration.

```kotlin
SentryNav3Effect(
  backStack = backStack,
  nameExtractor = { route ->
    when (route) {
      Home -> "home"
      is Profile -> "profile"
      is Article -> "article"
    }
  },
  argumentsExtractor = { route ->
    when (route) {
      Home -> emptyMap()
      is Profile -> mapOf("profile_type" to "member")
      is Article -> mapOf("slug" to route.slug)
    }
  },
)
```

The profile example deliberately avoids sending `userId`. If a route contains PII or secrets, redact
or replace those values before returning the map.

```kotlin
SentryNav3Effect(
  backStack = backStack,
  argumentsExtractor = { route ->
    when (route) {
      is Profile -> mapOf("user_id" to "[Filtered]")
      else -> emptyMap()
    }
  },
)
```

Supported argument values are `String`, `Number`, `Boolean`, `null`, `Map`, and `Collection`. Other
values are converted with `toString()` and a warning is logged.

Nested safe values are supported:

```kotlin
SentryNav3Effect(
  backStack = backStack,
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
  },
)
```

## Limit Back Stack Context

Sentry attaches the latest back-stack entries to `contexts.navigation.backstack`. The default limit
is 30 entries.

```kotlin
SentryNav3Effect(
  backStack = backStack,
  options =
    SentryNav3Options().apply {
      maxCapturedBackStackEntries = 10
    },
)
```

Set this based on how much navigation context is useful for debugging crashes and errors.

## Disable Specific Signals

You can disable individual parts of the integration.

```kotlin
SentryNav3Effect(
  backStack = backStack,
  options =
    SentryNav3Options().apply {
      enableNavigationBreadcrumbs = false
    },
)
```

```kotlin
SentryNav3Effect(
  backStack = backStack,
  options =
    SentryNav3Options().apply {
      enableNavigationTransactions = false
    },
)
```

```kotlin
SentryNav3Effect(
  backStack = backStack,
  options =
    SentryNav3Options().apply {
      captureBackStack = false
    },
)
```

Screen tracking follows the SDK's `SentryOptions.isEnableScreenTracking` setting.

## Recommended App-Shell Setup

The effect should live for the whole navigation session.

```kotlin
@Composable
fun RootScreen() {
  val backStack = remember { mutableStateListOf<Route>(Home) }

  SentryNav3Effect(
    backStack = backStack,
    options =
      SentryNav3Options().apply {
        maxCapturedBackStackEntries = 10
      },
    nameExtractor = { route ->
      when (route) {
        Home -> "home"
        is Profile -> "profile"
        is Article -> "article"
      }
    },
    argumentsExtractor = { route ->
      when (route) {
        Home -> emptyMap()
        is Profile -> mapOf("profile_type" to "member")
        is Article -> mapOf("slug" to route.slug)
      }
    },
  )

  NavDisplay(
    backStack = backStack,
    // Configure your Navigation 3 entries here.
  )
}
```

Avoid this pattern:

```kotlin
@Composable
fun ProfileScreen(backStack: SnapshotStateList<Route>) {
  // Do not put the effect inside a destination screen.
  SentryNav3Effect(backStack = backStack)
}
```

When `ProfileScreen` leaves composition, the integration is disposed and navigation is no longer
observed.

## Current Limitations

This integration observes one back stack. It does not model multiple retained back stacks, tabbed
navigation stacks, multipane visibility, or a custom primary visible route. Those patterns need a
later API that accepts app-owned multi-stack state.

Dialog entries are treated like regular back-stack entries. If your app represents dialogs as route
keys, they can produce breadcrumbs and navigation transactions like any other route.
