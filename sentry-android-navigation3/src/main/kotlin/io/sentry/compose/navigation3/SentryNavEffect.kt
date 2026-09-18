package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryOptions
import org.jetbrains.annotations.ApiStatus

/**
 * An effect for generating Sentry data from your Nav3 backstack. Configure it via [options] and
 * call it before you invoke your `NavDisplay`.
 *
 * ```kotlin
 *  @Composable
 *  fun AppNavigation() {
 *    val navBackStack = rememberNavBackStack(Home)
 *
 *    // Place SentryNavEffect in the same composable as your NavDisplay and call
 *    // the effect first. Doing so ensures the effect's lifecycle matches your
 *    // NavDisplay, and that any Sentry data produced by your nav destinations
 *    // get attributed to the appropriate nav transaction.
 *    SentryNavEffect(
 *      backStack = navBackStack,
 *      nameExtractor = { route -> route.extractName() },
 *      argumentsExtractor = { route -> route.extractArgument() },
 *      options = SentryNavOptions(),
 *    )
 *
 *    // Configure your NavDisplay like usual.
 *    NavDisplay(
 *      backStack = navBackStack,
 *      ...
 *    )
 *  }
 * ```
 *
 * **Data generated**
 *
 * By default, the following data is produced for each nav destination:
 *
 * - a breadcrumb
 * - a screen name
 * - a record of the current back stack (last 10 frames)
 *
 * A new transaction is started at each nav destination, assuming another non-nav transaction isn't
 * already active.
 *
 * You can configure the above defaults via [SentryNavOptions]. (Screen names can be disabled via
 * [SentryOptions.setEnableScreenTracking].)
 *
 * **Limitations**
 *
 * `SentryNavEffect` generates all Sentry data based solely on the top entry of your back stack. In
 * particular, it has no awareness of
 * [`Scene`](https://developer.android.com/guide/navigation/navigation-3/scenes)s. Transaction
 * routes, breadcrumbs, and screen names are all derived from the top entry of the back stack and
 * are updated as it changes.
 *
 * `SentryNavEffect` also doesn't make any special accommodations for
 * [predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
 * gestures. That means, for instance, that spans produced by predictively rendered composables can
 * show up under the current destination's transaction.
 *
 * @param backStack The navigation backstack to observe.
 * @param nameExtractor Extracts a human-readable route name from each entry of the [backStack].
 * @param argumentsExtractor Optional extractor for a map of argument name -> argument values from
 *   each entry of the [backStack]. If not provided, no arguments are attached.
 * @param options The kinds of navigation info this effect should record.
 */
@ApiStatus.Experimental
@Composable
@Suppress("FunctionNaming")
internal fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  nameExtractor: RouteNameExtractor<T>,
  argumentsExtractor: RouteArgumentsExtractor<T>? = null,
  options: SentryNavOptions = SentryNavOptions(),
) {
  SentryNavEffect(
    backStack = backStack,
    nameExtractor = nameExtractor,
    argumentsExtractor = argumentsExtractor,
    options = options,
    scopes = ScopesAdapter.getInstance(),
  )
}

@Composable
@Suppress("FunctionNaming")
internal fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  nameExtractor: RouteNameExtractor<T>,
  argumentsExtractor: RouteArgumentsExtractor<T>? = null,
  options: SentryNavOptions = SentryNavOptions(),
  scopes: IScopes,
) {
  val routeResolvers = rememberUpdatedState(RouteResolvers(nameExtractor, argumentsExtractor))

  val observer =
    remember(scopes, options) {
      BackStackObserver(
        scopes = scopes,
        options = options,
        resolvers = { routeResolvers.value },
      )
    }

  // The incoming back stack is mutable and shared with the host app; copy it so that BackStackKey
  // and BackStackObserver are guaranteed to have the same (stable) view.
  val copy = backStack.toList()

  DisposableEffect(observer, BackStackKey(copy)) {
    observer.onBackStackChanged(backStack = copy)
    onDispose {}
  }

  DisposableEffect(observer) {
    onDispose { observer.cleanup() }
  }
}
