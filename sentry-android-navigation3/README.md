# sentry-android-navigation3

This module provides an integration for [AndroidX Navigation 3](https://developer.android.com/guide/navigation/navigation-3).
It currently supports Android targets. The Maven artifact remains
`io.sentry:sentry-android-navigation3`, while public APIs live under the
`io.sentry.compose.navigation3` Kotlin package so imports can stay stable if the integration moves
to a multiplatform artifact later.

Please consult the documentation on how to install and use this integration in the Sentry Docs for [Android](https://docs.sentry.io/platforms/android/integrations/navigation3/).

## Single-stack navigation

For a standard Navigation 3 app with one back stack, call `SentryNav3NavigationEffect` from the
same long-lived composable that owns your `NavDisplay` state:

```kotlin
val backStack = remember { mutableStateListOf<Screen>(Home) }

SentryNav3NavigationEffect(
  backStack = backStack,
  nameExtractor = { screen -> screen.route },
)

NavDisplay(
  backStack = backStack,
  onBack = { backStack.removeLastOrNull() },
  entryProvider = entryProvider { /* ... */ },
)
```

This records navigation breadcrumbs, starts navigation transactions, updates `scope.screen`, and
attaches `contexts.navigation`. Single-stack apps use the same context shape as multiple-stack apps:

```json
{
  "selected_stack": "default",
  "stacks_in_use": ["default"],
  "backstacks": [
    {
      "name": "default",
      "selected": true,
      "in_use": true,
      "backstack": [{ "route": "/Home" }, { "route": "/Detail" }]
    }
  ]
}
```

## Multiple retained back stacks

Navigation 3 apps often own several retained stacks, for example one stack per bottom-navigation tab.
Use the multiple-stack overload when your app keeps a selected stack and a map of retained stack
snapshots:

```kotlin
enum class TopLevelStack { Home, Search, Profile }

val selectedStack: TopLevelStack = appNavigationState.selectedStack
val backStacks: Map<TopLevelStack, List<Screen>> = appNavigationState.backStacks
val stacksInUse: Set<TopLevelStack> = appNavigationState.renderedStacks

SentryNav3NavigationEffect(
  selectedStack = selectedStack,
  backStacks = backStacks,
  stacksInUse = stacksInUse,
  stackNameExtractor = { stack -> stack.name.lowercase() },
  nameExtractor = { screen -> screen.route },
)
```

`selectedStack` is the stack currently driving the user-visible route. Changing it is treated as a
navigation event. Mutating an inactive retained stack only refreshes crash context.

`backStacks` should contain each retained stack snapshot you want in crash context. `stacksInUse`
should contain the stack keys currently rendered by the UI. This can include multiple stacks on
large-screen or adaptive layouts, while a typical bottom-navigation layout usually contains only the
selected stack.

`stackNameExtractor` should return stable, readable stack names. String stack keys use their own
value by default; other key types default to their class simple name.

## Multipane and primary route selection

For layouts where Navigation 3 composes multiple entries at the same time, create a shared holder,
wire it to the back stack effect, and pass a decorator to `NavDisplay`:

```kotlin
val holder = rememberSentryNavStateHolder<Screen>(
  nameExtractor = { screen -> screen.route },
  primaryRouteSelector = { visibleEntries ->
    visibleEntries.firstOrNull { it.metadata["pane"] == "detail" }
  },
)
val sentryDecorator = rememberSentryNavEntryDecorator(holder)

SentryNav3NavigationEffect(backStack = backStack, holder = holder)

NavDisplay(
  backStack = backStack,
  entryDecorators = listOf(sentryDecorator),
  entryProvider = entryProvider { /* ... */ },
)
```

`primaryRouteSelector` receives the currently visible entries, including their route, metadata, and
stack name when known. Return the entry that should drive `scope.screen`, navigation breadcrumbs, and
transaction names. If the selector is omitted or does not return one of the provided entries, Sentry
prefers a visible entry from the selected stack and then falls back to built-in list-detail metadata
heuristics.

When visible entries are known, Sentry adds them to `contexts.navigation.visible_entries`:

```json
{
  "selected_stack": "search",
  "stacks_in_use": ["search", "profile"],
  "backstacks": [
    {
      "name": "search",
      "selected": true,
      "in_use": true,
      "backstack": [{ "route": "/Search" }, { "route": "/Result" }]
    },
    {
      "name": "profile",
      "selected": false,
      "in_use": true,
      "backstack": [{ "route": "/Profile" }]
    }
  ],
  "visible_entries": [
    { "stack": "search", "route": "/Result" },
    { "stack": "profile", "route": "/Profile" }
  ]
}
```

## Privacy note

By default this integration does **not** attach navigation route arguments. Arguments are only
captured when you supply an `argumentsExtractor`. Anything that extractor returns is sent to Sentry
as-is (in breadcrumbs, `contexts.navigation`, and the navigation transaction); it is not gated by
`SentryOptions.isSendDefaultPii()` and is not automatically PII-scrubbed. Route arguments frequently
contain PII or secrets (user IDs, email addresses, auth tokens or deep-link query params), so only
return values that are safe to send and redact sensitive data in the extractor (or via
`beforeBreadcrumb` / `beforeSend`).
