package io.sentry.samples.android.navigation.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Pushes keys that `BackStackEntryMapper.serialName()`, which auto-instrumentation uses, names
 * differently from the sample's own mapper, and lists the name each should record.
 *
 * Names are compile-time strings, so they should be the same in a minified release build.
 */
@Composable
internal fun RouteKeysRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    routeSpec = Nav3Route.RouteKeys.routeSpec(),
    testTagPrefix = nav3TestTag("route_route_keys"),
    cardContent = {
      routeKeyExamples.forEach { example ->
        RouteInfo("Manual", example.manualName, testTag = nav3TestTag("route_keys_manual"))
        RouteInfo("Auto", example.autoName, testTag = nav3TestTag("route_keys_auto"))
        RouteButton(
          "Push ${example.key.routeName}",
          onClick = { backStack.add(example.key) },
          testTag = nav3TestTag("route_keys_push_${example.key.routeName}"),
        )
      }
    },
  )
}

/** The destination shown for each of [routeKeyExamples]' keys. */
@Composable
internal fun RouteKeyScreen(route: Nav3Route, backStack: SnapshotStateList<Nav3Route>) {
  val example = routeKeyExamples.first { it.key::class == route::class }
  RouteScaffold(
    routeSpec = route.routeSpec(),
    testTagPrefix = nav3TestTag("route_key_${route.routeName}"),
    cardContent = {
      RouteInfo("Manual", example.manualName)
      RouteInfo("Auto", example.autoName)
      RouteButton(
        "Back",
        onClick = { backStack.removeLastOrNull() },
        testTag = nav3TestTag("route_key_back"),
      )
    },
  )
}
