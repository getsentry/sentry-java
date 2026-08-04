package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
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
 *      options = SentryNavOptions(maxCapturedBackStackEntries = 10),
 *      nameExtractor = { route -> route.extractName() },
 *      argumentsExtractor = { route -> route.extractArgument() },
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
 * **Privacy / PII**
 *
 * Values returned from [nameExtractor] and [argumentsExtractor] are ***not*** scrubbed by the
 * Sentry SDK before being sent to Sentry. Only return route names and arguments that are known to
 * be safe or have been pre-scrubbed.
 *
 * @param backStack The navigation backstack to observe.
 * @param scopes A scopes instance used to track generated Sentry data.
 * @param options The kinds of navigation info this effect should record.
 * @param nameExtractor Optional lambda to extract a human-readable route name from the top entry of
 *   the [backStack]. If not provided, defaults to the simple name of the entry's class.
 * @param argumentsExtractor Optional lambda to extract a map of argument name → argument values
 *   from the top entry of the [backStack]. If not provided, no arguments are attached. The
 *   following scalar values are supported: [String], [CharSequence], [Char], [Boolean], any
 *   [Number], enums (via [Enum.name]), and `null`. Supported container values are: [Array]s,
 *   primitive arrays, [Map]s, and [Collection]s of supported values, including nested containers.
 *   All other types are stringified via `toString()`. Cyclic or deeply nested containers are
 *   skipped. Return only the arguments needed for diagnostics and avoid large structures.
 */
@ApiStatus.Experimental
@Composable
@Suppress("FunctionNaming")
public fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  scopes: IScopes = ScopesAdapter.getInstance(),
  options: SentryNavOptions = SentryNavOptions(),
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
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

  val capturedBackStack = backStack.toList()

  DisposableEffect(observer, BackStackKey(capturedBackStack)) {
    observer.onBackStackChanged(backStack = capturedBackStack)
    onDispose {}
  }

  DisposableEffect(observer) {
    onDispose { observer.cleanup() }
  }
}

/**
 * Holds host app-defined extractors, which convert a back stack entry of type [T] into a route name
 * and a map of zero or more route arguments. Extracted values are eventually grouped into
 * [RouteEntry]s for display.
 *
 * Extractor invocations are hidden from Compose snapshot observation so they don't impact
 * invalidation of the recompose scope that reads them.
 */
internal class RouteResolvers<T : Any>(
  val nameExtractor: ((T) -> String)?,
  val argumentsExtractor: ((T) -> Map<String, Any?>)?,
) {

  fun getName(backStackEntry: T): String? = Snapshot.withoutReadObservation {
    nameExtractor?.invoke(backStackEntry)
  }

  fun getArguments(backStackEntry: T): Map<String, Any?>? = Snapshot.withoutReadObservation {
    argumentsExtractor?.invoke(backStackEntry)
  }
}

/**
 * A key for distinguishing back stacks over time.
 *
 * Lets `*Effect`s restart when either the identity of a stack entry changes or the stack's entries
 * are reordered.
 */
internal class BackStackKey<T : Any>(private val backStack: List<T>) {

  override fun equals(other: Any?): Boolean {
    // Use of identity rather than structural equality frees us from entries' equals() and
    // hashCode() implementations, which are provided by the host app and may be incomplete,
    // expensive, or incorrect for our purposes.
    if (this === other) {
      return true
    }
    if (other !is BackStackKey<*>) {
      return false
    }
    if (backStack.size != other.backStack.size) {
      return false
    }

    return backStack.indices.all { index -> backStack[index] === other.backStack[index] }
  }

  override fun hashCode(): Int {
    var result = backStack.size
    for (entry in backStack) {
      result = 31 * result + System.identityHashCode(entry)
    }
    return result
  }
}
